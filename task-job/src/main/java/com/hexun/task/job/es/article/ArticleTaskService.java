package com.hexun.task.job.es.article;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hexun.cdsq.enums.AuditStatusEnum;
import com.hexun.cdsq.model.article.Article;
import com.hexun.cdsq.model.article.Column;
import com.hexun.cdsq.service.article.ArticleAccessService;
import com.hexun.cdsq.service.article.ArticleLikeService;
import com.hexun.cdsq.service.article.ColumnService;
import com.hexun.common.utils.BeanUtils;
import com.hexun.es.enums.ArticleIndexFieldEnum;
import com.hexun.es.enums.IndexFieldEnum;
import com.hexun.es.model.SearchArticle;
import com.hexun.es.pojo.MappingField;
import com.hexun.px.enums.TrueFalseStatus;
import com.hexun.task.common.utils.CommonUtils;

/**
 * 文章任务服务
 * 
 * @author xiongyan
 * @date 2017年6月23日 下午5:08:49
 */
@Component
public class ArticleTaskService {

	@Autowired
	private ArticleAccessService articleAccessService;
	
	@Autowired
	private ArticleLikeService articleLikeService;
	
	@Autowired
	private ColumnService columnService;

	
	/**
	 * 封装文章索引映射字段信息
	 * 
	 * @param articleList
	 * @return
	 */
	public List<MappingField> articleIndexMappingField(List<Article> articleList) {
		// 获取所有栏目id和栏目名
		Map<Integer, Column> columnMap = queryArticleColumn();
		// 老师昵称
		Map<Long, String> teacherMap = CommonUtils.teacherMap(articleList, IndexFieldEnum.TEACHERID.getField());
		
		List<MappingField> list = new ArrayList<>();
		SearchArticle searchArticle;
		for (Article article : articleList) {
			searchArticle = new SearchArticle();
			BeanUtils.copyA2B(article, searchArticle);
			searchArticle.setId(article.getId().toString());
			searchArticle.setTeacherName(teacherMap.get(article.getTeacherId()));
			if (article.getColumnId() != 0) {
				Column column = columnMap.get(article.getColumnId());
				if (null != column) {
					searchArticle.setColumnName(column.getColumnName());
					searchArticle.setProductId(column.getProductId());
					searchArticle.setPriceId(column.getPriceId());
				}
			}
			searchArticle.setArticleContent(article.getContent());
			searchArticle.setArticleContentSearch(CommonUtils.removeHtml(article.getContent()));
			// 访问数，默认为0
			searchArticle.setAccessNum(0);
			// 周访问数，默认为0
			searchArticle.setWeekAccessNum(0);
			// 点赞数，默认为0
			searchArticle.setLikeNum(0);
			list.add(searchArticle);
		}
		return list;
	}
	
	/**
	 * 获取所有栏目
	 * 
	 * @return
	 */
	public Map<Integer, Column> queryArticleColumn() {
		Map<String, Object> map = new HashMap<>();
		// 未删除
		map.put(IndexFieldEnum.ISDELETE.getField(), TrueFalseStatus.FALSE.getValue());
		// 审核通过
		map.put(ArticleIndexFieldEnum.AUDITSTATUS.getField(), AuditStatusEnum.AUDIT_PASS.getValue());
		List<Column> columns = columnService.listColumn(map);
		if (CollectionUtils.isEmpty(columns)) {
			return Collections.emptyMap();
		}
		
		Map<Integer, Column> columnMap = new HashMap<>();
		for (Column column : columns) {
			columnMap.put(column.getId(), column);
		}
		return columnMap;
	}
	
	/**
	 * 文章访问数
	 * 
	 * @param sourceMap
	 */
	public void countArticleAccessNum(Map<String, Map<String, Object>> sourceMap) {
		Map<String, Integer> map = new HashMap<>();
		// 周访问数
		map.put(ArticleIndexFieldEnum.WEEKACCESSNUM.getField(), 7);
		List<Map<String, Object>> accessNumList = articleAccessService.countArticleAccessNum(map);
		if (CollectionUtils.isEmpty(accessNumList)) {
			return;
		}
		putSourceMap(accessNumList, sourceMap);
	}
	
	/**
	 * 文章点赞数
	 * 
	 * @param sourceMap
	 */
	public void countArticleLikeNum(Map<String, Map<String, Object>> sourceMap) {
		List<Map<String, Object>> likeNumList = articleLikeService.countArticleLikeNum();
		if (CollectionUtils.isEmpty(likeNumList)) {
			return;
		}
		putSourceMap(likeNumList, sourceMap);
	}
	
	/**
	 * map
	 * 
	 * @param list
	 * @param sourceMap
	 */
	private void putSourceMap(List<Map<String, Object>> list, Map<String, Map<String, Object>> sourceMap) {
		for (Map<String, Object> map : list) {
			String id = map.remove(IndexFieldEnum.ID.getField()).toString();
			Map<String, Object> source = sourceMap.get(id);
			if (null == source) {
				sourceMap.put(id, map);
			} else {
				source.putAll(map);
			}
		}
	}
	
}
