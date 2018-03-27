package com.hexun.task.job.es.lesson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hexun.common.utils.BeanUtils;
import com.hexun.common.utils.StringUtils;
import com.hexun.es.enums.ChargeTypeEnum;
import com.hexun.es.enums.IndexFieldEnum;
import com.hexun.es.model.Nested;
import com.hexun.es.model.SearchClass;
import com.hexun.es.pojo.MappingField;
import com.hexun.px.enums.ClassType;
import com.hexun.px.model.Class;
import com.hexun.px.model.ClassSection;
import com.hexun.px.model.CommentRate;
import com.hexun.px.model.VisitNum;
import com.hexun.px.pojo.FreeClass;
import com.hexun.px.service.ClassSectionService;
import com.hexun.px.service.CommentRateService;
import com.hexun.px.service.OpenClassService;
import com.hexun.px.service.OpenForKjService;
import com.hexun.px.service.VisitNumService;
import com.hexun.task.common.utils.CommonUtils;

/**
 * 课程任务服务
 * 
 * @author xiongyan
 * @date 2017年6月23日 下午5:08:49
 */
@Component
public class ClassTaskService {

	@Autowired
	private OpenClassService openClassService;

	@Autowired
	private OpenForKjService openForKjService;
	
	@Autowired
	private ClassSectionService classSectionService;
	
	@Autowired
	private CommentRateService commentRateService;
	
	@Autowired
	private VisitNumService visitNumService;
	

	/**
	 * 课节id对应的章节信息
	 * 
	 * @param map
	 * @return
	 */
	public Map<Integer, ClassSection> getSectionIdPackIdMap(Map<String, Object> map) {
		List<ClassSection> classSectionList = classSectionService.queryClassSectionByMap(map);
		if (CollectionUtils.isEmpty(classSectionList)) {
			return Collections.emptyMap();
		}
		Map<Integer, ClassSection> sectionIdPackIdMap = new HashMap<>();
		for (ClassSection classSection : classSectionList) {
			// 课节id对应的章节信息
			sectionIdPackIdMap.put(classSection.getClassId(), classSection);
		}
		return sectionIdPackIdMap;
	}
	
	/**
	 * 限时免费课
	 * 
	 * @return
	 */
	public Map<Integer, FreeClass> getFreeClassMap() {
		List<FreeClass> freeClassList = openForKjService.getFreeClass(new Date(0));
		if (CollectionUtils.isEmpty(freeClassList)) {
			return Collections.emptyMap();
		}
		Map<Integer, FreeClass> freeClassMap = new HashMap<>();
		for (FreeClass freeClass : freeClassList) {
			// 点播课
			freeClassMap.put(freeClass.getClassId(), freeClass);
			// 套课
			freeClassMap.put(freeClass.getpClassId(), freeClass);
		}
		return freeClassMap;
	}
	
	/**
	 * 公开免费课
	 * 
	 * @return
	 */
	public Map<Integer, FreeClass> getOpenClassMap() {
		List<FreeClass> freeClassList = openClassService.getOpenClass(new Date(0));
		if (CollectionUtils.isEmpty(freeClassList)) {
			return Collections.emptyMap();
		}
		Map<Integer, FreeClass> freeClassMap = new HashMap<>();
		for (FreeClass freeClass : freeClassList) {
			// 直播课
			freeClassMap.put(freeClass.getClassId(), freeClass);
		}
		return freeClassMap;
	}
	
	/**
	 * 获取课程评论数
	 * 
	 * @param map
	 * @return
	 */
	public Map<Integer, CommentRate> getCommentRateMap(Map<String, Object> map) {
		List<CommentRate> commentRateList = commentRateService.queryCommentRateByMap(map);
		if (CollectionUtils.isEmpty(commentRateList)) {
			return Collections.emptyMap();
		}
		Map<Integer, CommentRate> commentRateMap = new HashMap<>();
		for (CommentRate commentRate : commentRateList) {
			// 课程评论数，评论率
			commentRateMap.put(commentRate.getClassId(), commentRate);
		}
		return commentRateMap;
	}
	
	/**
	 * 获取课程学习数
	 * 
	 * @return
	 */
	public Map<Integer, VisitNum> getVisitNumMap() {
		List<VisitNum> visitNumList = visitNumService.queryVisitNum();
		if (CollectionUtils.isEmpty(visitNumList)) {
			return Collections.emptyMap();
		}
		Map<Integer, VisitNum> visitNumMap = new HashMap<>();
		for (VisitNum visitNum : visitNumList) {
			// 课程学习数，月学习数
			visitNumMap.put(visitNum.getClassId(), visitNum);
		}
		return visitNumMap;
	}
	
	/**
	 * 封装课程索引映射字段信息
	 * 
	 * @param map
	 * @param classList
	 * @return
	 */
	public List<MappingField> classIndexMappingField(Map<String, Object> map, List<Class> classList) {
		// 课节信息
		Map<Integer, ClassSection> sectionIdPackIdMap = getSectionIdPackIdMap(map);
		// 老师昵称
		Map<Long, String> teacherMap = CommonUtils.teacherMap(classList, IndexFieldEnum.TEACHERID.getField());
		// 限时免费课
		Map<Integer, FreeClass> freeClassMap = getFreeClassMap();
		// 公开免费课
		Map<Integer, FreeClass> openClassMap = getOpenClassMap();
		// 评论数
		Map<Integer, CommentRate> commentRateMap = getCommentRateMap(map);
		// 学习数
		Map<Integer, VisitNum> visitNumMap = getVisitNumMap();
		
		List<MappingField> list = new ArrayList<>();
		SearchClass searchClass;
		for (Class clazz : classList) {
			searchClass = new SearchClass();
			BeanUtils.copyA2B(clazz, searchClass);
			searchClass.setId(clazz.getClassId().toString());
			searchClass.setTeacherName(teacherMap.get(clazz.getTeacherId()));
			searchClass.setVhallPlayUrl(clazz.getPlayUrl1());
			searchClass.setGenseePlayUrl(clazz.getPlayUrl2());
			searchClass.setChargeType(ChargeTypeEnum.CHARGE.getValue());
			// 套课，章节，课节
			ClassSection classSection = sectionIdPackIdMap.get(clazz.getClassId());
			if (null != classSection) {
				searchClass.setChapterId(classSection.getChapterId());
				searchClass.setPackId(classSection.getPackId());
				searchClass.setPackClassId(classSection.getPackClassId());
			}
			// 热门标签
			String classHotLabel = clazz.getClassHotLabel();
			if (StringUtils.isNotEmpty(classHotLabel)) {
				searchClass.setClassHotLabel(classHotLabelNested(classHotLabel));
			}
			// 课程进阶
			searchClass.setInvestMethod(clazz.getInvestMethods());
			// 限时免费课
			FreeClass freeClass = freeClassMap.get(clazz.getClassId());
			if (null != freeClass) {
				if (ClassType.DB.getValue().equals(clazz.getClassType())) {
					searchClass.setFreeStartTime(freeClass.getBeginTime());
					searchClass.setFreeEndTime(freeClass.getEndTime());
				}
				searchClass.setChargeType(ChargeTypeEnum.TIME_FREE.getValue());
			}
			// 公开免费课
			FreeClass openClass = openClassMap.get(clazz.getClassId());
			if (null != openClass) {
				searchClass.setFreeStartTime(openClass.getBeginTime());
				searchClass.setFreeEndTime(openClass.getEndTime());
				searchClass.setOpenClassId(openClass.getOpenClassId());
				searchClass.setOpenClassName(openClass.getOpenClassName());
				searchClass.setOpenClassDateDesc(openClass.getPlayTime());
				searchClass.setOpenClassImage(openClass.getClassPic());
				searchClass.setOpenClassIntro(openClass.getOpenClassIntro());
				searchClass.setChargeType(ChargeTypeEnum.OPEN_FREE.getValue());
			}
			// 评论数，好评率
			CommentRate commentRate = commentRateMap.get(clazz.getClassId());
			if (null != commentRate) {
				searchClass.setCommentNum(commentRate.getGoodCommentNum() + commentRate.getOkCommentNum() + commentRate.getBadCommentNum());
				searchClass.setGoodRate(commentRate.getGoodCommentRate().doubleValue());
			} else {
				// 评论数，默认为0
				searchClass.setCommentNum(0);
				// 好评率，默认为0.0
				searchClass.setGoodRate(0.0);
			}
			// 学习数，月学习数
			VisitNum visitNum = visitNumMap.get(clazz.getClassId());
			if (null != visitNum) {
				searchClass.setLearnNum(visitNum.getVisitNum());
				searchClass.setMonthLearnNum(visitNum.getMonthVisitNum());
			} else {
				// 学习数，默认为0
				searchClass.setLearnNum(0); 
				// 月学习数，默认为0
				searchClass.setMonthLearnNum(0); 
			}
			list.add(searchClass);
		}
		return list;
	}
	
	/**
	 * 课程热门标签聚合
	 * 
	 * @param classHotLabel
	 * @return
	 */
	public List<Nested> classHotLabelNested(String classHotLabel) {
		List<Nested> classHotLabels = new ArrayList<>();
		for (String label : StringUtils.split2Set(classHotLabel)) {
			classHotLabels.add(new Nested(label));
		}
		return classHotLabels;
	}
	
}
