package com.hexun.task.job.es.lesson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hexun.common.utils.ListPageUtil;
import com.hexun.es.enums.IndexFieldEnum;
import com.hexun.es.enums.IndexTypeEnum;
import com.hexun.es.pojo.MappingField;
import com.hexun.es.service.SearchClassService;
import com.hexun.job.common.AbstractShardingTask;
import com.hexun.px.enums.TrueFalseStatus;
import com.hexun.px.model.Class;
import com.hexun.px.service.ClassService;

/**
 * 课程全量索引任务
 * 
 * @author xiongyan
 * @date 2017年3月10日 上午11:13:54
 */
@Component
public class ClassFullDoseTask extends AbstractShardingTask {

	private static final Logger logger = LoggerFactory.getLogger(ClassFullDoseTask.class);

	@Autowired
	private ClassTaskService classTaskService;
	
	@Autowired
	private ClassService classService;

	@Autowired
	private SearchClassService searchClassService;
	
	/**
	 * 每页查询条数
	 */
	private static final int PAGESIZE = 500;

	@Override
	public String getTaskName() {
		return "课程全量索引任务";
	}

	@Override
	public void doExecute() {
		// 获取课程全量信息
		Map<String, Object> map = new HashMap<>();
		// 未删除
		map.put(IndexFieldEnum.ISDELETE.getField(), TrueFalseStatus.FALSE.getValue());
		// 查询课程总数
		Long count = classService.countClass(map);
		if (null == count || count == 0) {
			return;
		}
		
		// 总页数
		int num = ListPageUtil.getTotalPages(count.intValue(), PAGESIZE);
		
		for (int i = 1; i <= num; i++) {
			// 分页查询课程想
			List<Class> classList = classService.listClassPage(map, i, PAGESIZE);
			// 课程索引信息
			List<MappingField> list = classTaskService.classIndexMappingField(map, classList);
			if (i == 1) {
				// 全量索引
				searchClassService.fullIndex(IndexTypeEnum.LESSON, list);
			} else {
				// 增量索引
				searchClassService.incrementIndex(IndexTypeEnum.LESSON, list);
			}
		}
		logger.info("课程全量索引任务成功!");
	}
	
}
