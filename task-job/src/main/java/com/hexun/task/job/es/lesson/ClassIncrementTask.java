package com.hexun.task.job.es.lesson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hexun.es.enums.ChargeTypeEnum;
import com.hexun.es.enums.ClassIndexFieldEnum;
import com.hexun.es.enums.IndexFieldEnum;
import com.hexun.es.enums.IndexTypeEnum;
import com.hexun.es.model.SearchClass;
import com.hexun.es.pojo.ClassQueryRequest;
import com.hexun.es.pojo.QueryResponse;
import com.hexun.es.service.SearchClassService;
import com.hexun.job.common.AbstractShardingTask;
import com.hexun.px.enums.TrueFalseStatus;
import com.hexun.px.pojo.FreeClass;
import com.hexun.px.service.OpenClassService;
import com.hexun.px.service.OpenForKjService;

/**
 * 课程增量索引任务
 * 
 * @author xiongyan
 * @date 2017年9月4日 下午12:28:36
 */
@Component
public class ClassIncrementTask extends AbstractShardingTask {

	private static final Logger logger = LoggerFactory.getLogger(ClassIncrementTask.class);

	@Autowired
	private SearchClassService searchClassService;
	
	@Autowired
	private OpenForKjService openForKjService;
	
	@Autowired
	private OpenClassService openClassService;
	
	
	@Override
	public String getTaskName() {
		return "课程增量索引任务";
	}

	@Override
	public void doExecute() {
		// 查询免费课
		List<SearchClass> list = new ArrayList<>();
		ClassQueryRequest request = new ClassQueryRequest();
		request.setFree(TrueFalseStatus.TRUE.getValue());
		request.setPageSize(0);
		QueryResponse<SearchClass> response = searchClassService.searchList(request);
		if (null != response && response.getTotal() > 0) {
			request.setPageSize(response.getTotal().intValue());
			request.setIncludeFields(Arrays.asList(IndexFieldEnum.ID.getField(), ClassIndexFieldEnum.CHARGETYPE.getField()));
			response = searchClassService.searchList(request);
			if (null != response && CollectionUtils.isNotEmpty(response.getList())) {
				list = response.getList();
			}
		}
		
		// 重置数据
		Map<String, Map<String, Object>> resetSourceMap = resetData(list);
		// 免费课
		Map<String, Map<String, Object>> freeSourceMap = freeData();
		// 并集
		resetSourceMap.putAll(freeSourceMap);
		
		// 更新索引
		searchClassService.updateIndex(IndexTypeEnum.LESSON, resetSourceMap);
		logger.info("课程增量索引成功!");
		
	}
	
	/**
	 * 重置数据
	 * 
	 * @param list
	 * @return
	 */
	private Map<String, Map<String, Object>> resetData(List<SearchClass> list) {
		Map<String, Map<String, Object>> sourceMap = new HashMap<>();
		Map<String, Object> source = null;
		for (SearchClass clazz : list) {
			source = new HashMap<>();
			// 免费开始时间
			source.put(ClassIndexFieldEnum.FREESTARTTIME.getField(), null);
			// 免费结束时间
			source.put(ClassIndexFieldEnum.FREEENDTIME.getField(), null);
			if (ChargeTypeEnum.OPEN_FREE.getValue().equals(clazz.getChargeType())) {
				// 公开课id
				source.put(ClassIndexFieldEnum.OPENCLASSID.getField(), null);
				// 公开课名称
				source.put(ClassIndexFieldEnum.OPENCLASSNAMESTR.getField(), null);
				// 公开课授课时间
				source.put(ClassIndexFieldEnum.OPENCLASSDATEDESC.getField(), null);
				// 公开课图片
				source.put(ClassIndexFieldEnum.OPENCLASSIMAGE.getField(), null);
				// 公开课简介
				source.put(ClassIndexFieldEnum.OPENCLASSINTROSTR.getField(), null);
			}
			// 收费
			source.put(ClassIndexFieldEnum.CHARGETYPE.getField(), ChargeTypeEnum.CHARGE.getValue());
			sourceMap.put(clazz.getId(), source);
		}
		return sourceMap;
	}
	
	/**
	 * 免费课
	 * 
	 * @return
	 */
	private Map<String, Map<String, Object>> freeData() {
		Map<String, Map<String, Object>> sourceMap = new HashMap<>();
		// 限时免费课
		List<FreeClass> freeClassList = openForKjService.getFreeClass(new Date(0));
		if (CollectionUtils.isNotEmpty(freeClassList)) {
			Map<String, Object> source = null;
			for (FreeClass freeClass : freeClassList) {
				source = new HashMap<>();
				// 免费开始时间
				source.put(ClassIndexFieldEnum.FREESTARTTIME.getField(), freeClass.getBeginTime().getTime());
				// 免费结束时间
				source.put(ClassIndexFieldEnum.FREEENDTIME.getField(), freeClass.getEndTime().getTime());
				// 限时免费
				source.put(ClassIndexFieldEnum.CHARGETYPE.getField(), ChargeTypeEnum.TIME_FREE.getValue());
				sourceMap.put(freeClass.getClassId().toString(), source);
				if (freeClass.getpClassId() > 0 && !sourceMap.containsKey(freeClass.getpClassId().toString())) {
					Map<String, Object> packSource = new HashMap<>();
					packSource.put(ClassIndexFieldEnum.CHARGETYPE.getField(), ChargeTypeEnum.TIME_FREE.getValue());
					sourceMap.put(freeClass.getpClassId().toString(), packSource);
				}
			}
		}
		
		// 公开免费课
		List<FreeClass> openClassList = openClassService.getOpenClass(new Date(0));
		if (CollectionUtils.isNotEmpty(openClassList)) {
			Map<String, Object> source = null;
			for (FreeClass openClass : openClassList) {
				source = new HashMap<>();
				// 免费开始时间
				source.put(ClassIndexFieldEnum.FREESTARTTIME.getField(), openClass.getBeginTime().getTime());
				// 免费结束时间
				source.put(ClassIndexFieldEnum.FREEENDTIME.getField(), openClass.getEndTime().getTime());
				// 公开课id
				source.put(ClassIndexFieldEnum.OPENCLASSID.getField(), openClass.getOpenClassId());
				// 公开课名称
				source.put(ClassIndexFieldEnum.OPENCLASSNAMESTR.getField(), openClass.getOpenClassName());
				// 公开课授课时间
				source.put(ClassIndexFieldEnum.OPENCLASSDATEDESC.getField(), openClass.getPlayTime());
				// 公开课图片
				source.put(ClassIndexFieldEnum.OPENCLASSIMAGE.getField(), openClass.getClassPic());
				// 公开课简介
				source.put(ClassIndexFieldEnum.OPENCLASSINTROSTR.getField(), openClass.getOpenClassIntro());
				// 公开免费
				source.put(ClassIndexFieldEnum.CHARGETYPE.getField(), ChargeTypeEnum.OPEN_FREE.getValue());
				sourceMap.put(openClass.getClassId().toString(), source);
			}
		}
		return sourceMap;
	}
	
}
