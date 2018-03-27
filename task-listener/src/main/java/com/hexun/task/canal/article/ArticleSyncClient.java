package com.hexun.task.canal.article;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alibaba.otter.canal.protocol.CanalEntry.EventType;
import com.hexun.cdsq.enums.AuditStatusEnum;
import com.hexun.cdsq.model.article.ArticleContent;
import com.hexun.cdsq.model.article.ArticleLike;
import com.hexun.cdsq.model.article.Column;
import com.hexun.cdsq.service.article.ColumnService;
import com.hexun.common.utils.BeanUtils;
import com.hexun.es.enums.ArticleIndexFieldEnum;
import com.hexun.es.enums.IndexTypeEnum;
import com.hexun.es.enums.UpdateTypeEnum;
import com.hexun.es.model.SearchArticle;
import com.hexun.es.pojo.ArticleQueryRequest;
import com.hexun.es.pojo.QueryResponse;
import com.hexun.es.pojo.UpdateByQuery;
import com.hexun.es.service.SearchArticleService;
import com.hexun.hwcommon.service.UserAuth;
import com.hexun.online.exception.OnlineException;
import com.hexun.task.canal.BinlogInfo;
import com.hexun.task.canal.BinlogObserver;
import com.hexun.task.canal.CanalConnectorClient;
import com.hexun.task.common.utils.CommonUtils;

/**
 * 文章数据同步索引客户端
 * 
 * @author xiongyan
 * @date 2017年8月4日 上午10:10:44
 */
@Component
public class ArticleSyncClient implements InitializingBean, BinlogObserver {
	
	/**
	 * logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(ArticleSyncClient.class);

	@Autowired
	private ColumnService columnService;
	
	@Autowired
	private SearchArticleService searchArticleService;
	
	@Autowired
	private CanalConnectorClient canalClient;
	
	/**
	 * 文章点赞数
	 */
	private static Map<Integer, Integer> likeNumMap = new HashMap<>();
	
	/**
	 * bean初始化时注册观察者
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		canalClient.addObserver("cdsq#cdsq_column", this);
		canalClient.addObserver("cdsq#cdsq_article", this);
		canalClient.addObserver("cdsq#cdsq_article_content", this);
		canalClient.addObserver("cdsq#cdsq_article_like", this);
	}

	/**
	 * 消息
	 * 
	 * @param binlogInfo
	 */
	@Override
	public void message(BinlogInfo binlogInfo) {
		try {
			logger.info("文章数据库日志：{}", binlogInfo);
			switch (binlogInfo.getTableName()) {
				case "cdsq_column" :
					column(binlogInfo.getEventType(), binlogInfo.getData());
					break;
				case "cdsq_article" :
					article(binlogInfo.getEventType(), binlogInfo.getData());
					break;
				case "cdsq_article_content" :
					articleContent(binlogInfo.getData());
					break;
				case "cdsq_article_like" :
					articleLike(binlogInfo.getData());
					break;
				default :
					break;
			}
		} catch (Exception e) {
			throw new OnlineException("日志消费失败", e);
		}
	}
	
	/**
	 * 根据栏目id更新栏目名称
	 * 
	 * @param eventType
	 * @param data
	 * @throws InstantiationException 
	 * @throws IllegalAccessException 
	 */
	private void column(EventType eventType, Map<String, Object> data) throws IllegalAccessException, InstantiationException {
		if (EventType.UPDATE != eventType) {
			return;
		}
		Column column = BeanUtils.fromMap(data, Column.class);
		if (column.getIsDelete() || !AuditStatusEnum.AUDIT_PASS.getValue().equals(column.getAuditStatus())) {
			return;
		}
		
		// 根据栏目id查询栏目名称
		String columnName = getColumnInfo(column.getId());
		if (StringUtils.isEmpty(columnName) || columnName.equals(column.getColumnName())) {
			return;
		}
		
		UpdateByQuery updateByQuery = new UpdateByQuery();
		updateByQuery.setQueryKey(ArticleIndexFieldEnum.COLUMNID.getField());
		updateByQuery.setQueryValue(column.getId());
		updateByQuery.setUpdateKey(ArticleIndexFieldEnum.COLUMNNAMESTR.getField());
		updateByQuery.setUpdateValue(column.getColumnName());
		updateByQuery.setUpdateType(UpdateTypeEnum.STR);
		// 文章增量索引
		searchArticleService.updateByQuery(IndexTypeEnum.ARTICLE, updateByQuery);
	}
	
	/**
	 * 根据栏目id查询栏目名称
	 * 
	 * @param columnId
	 * @return
	 */
	private String getColumnInfo(Integer columnId) {
		ArticleQueryRequest request = new ArticleQueryRequest();
		request.setColumnId(columnId);
		request.setPageSize(1);
		request.setIncludeFields(Arrays.asList(ArticleIndexFieldEnum.COLUMNNAMESTR.getField()));
		QueryResponse<SearchArticle> response = searchArticleService.searchList(request);
		if (null == response || CollectionUtils.isEmpty(response.getList())) {
			return StringUtils.EMPTY;
		}
		return response.getList().get(0).getColumnName();
	}
	
	/**
	 * 文章实时索引
	 * 
	 * @param eventType
	 * @param data
	 * @throws InstantiationException 
	 * @throws IllegalAccessException 
	 */
	private void article(EventType eventType, Map<String, Object> data) throws IllegalAccessException, InstantiationException {
		SearchArticle searchArticle = BeanUtils.fromMap(data, SearchArticle.class);
		searchArticle.setTeacherName(UserAuth.GetNickNameByUserID(searchArticle.getTeacherId()));
		Integer columnId = searchArticle.getColumnId();
		if (columnId != 0) {
			Column column = columnService.selectByPrimaryKey(columnId);
			if (null != column) {
				searchArticle.setColumnName(column.getColumnName());
				searchArticle.setProductId(column.getProductId());
				searchArticle.setPriceId(column.getPriceId());
			}
		}
		if (EventType.INSERT == eventType) {
			// 访问数，默认为0
			searchArticle.setAccessNum(0);
			// 周访问数，默认为0
			searchArticle.setWeekAccessNum(0);
			// 点赞数，默认为0
			searchArticle.setLikeNum(0);
		}
		// 文章增量索引
		searchArticleService.incrementIndex(IndexTypeEnum.ARTICLE, searchArticle);
	}
	
	/**
	 * 根据文章id更新文章内容
	 * 
	 * @param data
	 * @throws InstantiationException 
	 * @throws IllegalAccessException 
	 */
	private void articleContent(Map<String, Object> data) throws IllegalAccessException, InstantiationException {
		ArticleContent articleContent = BeanUtils.fromMap(data, ArticleContent.class);
		Map<String, Object> source = new HashMap<>();
		source.put(ArticleIndexFieldEnum.ARTICLECONTENT.getField(), articleContent.getContent());
		source.put(ArticleIndexFieldEnum.ARTICLECONTENTSEARCHSTR.getField(), CommonUtils.removeHtml(articleContent.getContent()));
		// 文章增量索引
		searchArticleService.updateIndex(IndexTypeEnum.ARTICLE, articleContent.getArticleId().toString(), source);
	}
	
	/**
	 * 根据文章id更新文章点赞数
	 * 
	 * @param data
	 * @throws InstantiationException 
	 * @throws IllegalAccessException 
	 */
	private void articleLike(Map<String, Object> data) throws IllegalAccessException, InstantiationException {
		ArticleLike articleLike = BeanUtils.fromMap(data, ArticleLike.class);
		Integer likeNum = likeNumMap.get(articleLike.getArticleId());
		if (null == likeNum) {
			// 查询文章点赞数
			ArticleQueryRequest request = new ArticleQueryRequest();
			request.setId(articleLike.getArticleId().toString());
			request.setIncludeFields(Arrays.asList(ArticleIndexFieldEnum.LIKENUM.getField()));
			QueryResponse<SearchArticle> response = searchArticleService.searchList(request);
			if (null == response || CollectionUtils.isEmpty(response.getList())) {
				return;
			}
			likeNum = response.getList().get(0).getLikeNum();
		}
		// 文章点赞数+1
		likeNumMap.put(articleLike.getArticleId(), ++likeNum);
		
		Map<String, Object> source = new HashMap<>();
		source.put(ArticleIndexFieldEnum.LIKENUM.getField(), likeNum);
		// 文章增量索引
		searchArticleService.updateIndex(IndexTypeEnum.ARTICLE, articleLike.getArticleId().toString(), source);
	}
	
}
