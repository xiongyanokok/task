package com.hexun.task.job.es.article;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hexun.cdsq.service.article.ArticleAccessService;
import com.hexun.es.enums.ArticleIndexFieldEnum;
import com.hexun.es.enums.IndexFieldEnum;
import com.hexun.es.enums.IndexTypeEnum;
import com.hexun.es.service.SearchArticleService;
import com.hexun.job.common.AbstractShardingTask;

/**
 * 更新文章访问数任务
 * 
 * @author xiongyan
 * @date 2017年8月7日 上午9:16:25
 */
@Component
public class ArticleAccessTask extends AbstractShardingTask {

	private static final Logger logger = LoggerFactory.getLogger(ArticleAccessTask.class);

	@Autowired
	private ArticleAccessService articleAccessService;

	@Autowired
	private SearchArticleService searchArticleService;
	
	@Override
	public String getTaskName() {
		return "更新文章访问数任务";
	}

	@Override
	public void doExecute() {
		// 文章总访问数和周访问数
		Map<String, Integer> map = new HashMap<>();
		// 周访问数
		map.put(ArticleIndexFieldEnum.WEEKACCESSNUM.getField(), 7);
		List<Map<String, Object>> accessNumList = articleAccessService.countArticleAccessNum(map);
		if (CollectionUtils.isEmpty(accessNumList)) {
			return;
		}
		
		Map<String, Map<String, Object>> sourceMap = new HashMap<>();
		for (Map<String, Object> accessNumMap : accessNumList) {	
			sourceMap.put(accessNumMap.remove(IndexFieldEnum.ID.getField()).toString(), accessNumMap);
		}
		if (MapUtils.isNotEmpty(sourceMap)) {	
			// 更新索引
			searchArticleService.updateIndex(IndexTypeEnum.ARTICLE, sourceMap);
			logger.info("更新文章访问数成功!");
		}
	}
	
}
