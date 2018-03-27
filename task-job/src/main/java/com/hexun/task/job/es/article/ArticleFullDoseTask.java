package com.hexun.task.job.es.article;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hexun.cdsq.model.article.Article;
import com.hexun.cdsq.service.article.ArticleService;
import com.hexun.common.utils.ListPageUtil;
import com.hexun.es.enums.IndexFieldEnum;
import com.hexun.es.enums.IndexTypeEnum;
import com.hexun.es.pojo.MappingField;
import com.hexun.es.service.SearchArticleService;
import com.hexun.job.common.AbstractShardingTask;
import com.hexun.px.enums.TrueFalseStatus;

/**
 * 文章全量索引任务
 * 
 * @author xiongyan
 * @date 2017年7月10日 上午11:13:54
 */
@Component
public class ArticleFullDoseTask extends AbstractShardingTask {

	private static final Logger logger = LoggerFactory.getLogger(ArticleFullDoseTask.class);

	@Autowired
	private ArticleTaskService articleTaskService;
	
	@Autowired
	private ArticleService articleService;

	@Autowired
	private SearchArticleService searchArticleService;
	
	/**
	 * 每页查询条数
	 */
	private static final int PAGESIZE = 100;

	@Override
	public String getTaskName() {
		return "文章全量索引任务";
	}

	@Override
	public void doExecute() {
		Map<String, Object> map = new HashMap<>();
		// 未删除
		map.put(IndexFieldEnum.ISDELETE.getField(), TrueFalseStatus.FALSE.getValue());
		// 查询文章总数
		Long count = articleService.countArticle(map);
		if (null == count || count == 0) {
			return;
		}
		
		// 总页数
		int num = ListPageUtil.getTotalPages(count.intValue(), PAGESIZE);
		
		for (int i = 1; i <= num; i++) {
			// 分页查询文章信息
			List<Article> articleList = articleService.listArticlePage(map, i, PAGESIZE);
			// 文章索引信息
			List<MappingField> list = articleTaskService.articleIndexMappingField(articleList);
			if (i == 1) {
				// 全量索引
				searchArticleService.fullIndex(IndexTypeEnum.ARTICLE, list);
			} else {
				// 增量索引
				searchArticleService.incrementIndex(IndexTypeEnum.ARTICLE, list);
			}
		}
		
		
		Map<String, Map<String, Object>> sourceMap = new HashMap<>();
		// 文章访问数
		articleTaskService.countArticleAccessNum(sourceMap);
		// 文章点赞数
		articleTaskService.countArticleLikeNum(sourceMap);
		
		if (!sourceMap.isEmpty()) {
			// 更新索引
			searchArticleService.updateIndex(IndexTypeEnum.ARTICLE, sourceMap);
		}
		logger.info("文章全量索引任务成功!");
	}
	
}
