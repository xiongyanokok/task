package com.hexun.task.canal.lesson;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alibaba.otter.canal.protocol.CanalEntry.EventType;
import com.hexun.common.utils.BeanUtils;
import com.hexun.common.utils.StringUtils;
import com.hexun.es.enums.ChargeTypeEnum;
import com.hexun.es.enums.ClassIndexFieldEnum;
import com.hexun.es.enums.IndexTypeEnum;
import com.hexun.es.model.Nested;
import com.hexun.es.model.SearchClass;
import com.hexun.es.service.SearchClassService;
import com.hexun.hwcommon.service.UserAuth;
import com.hexun.online.exception.OnlineException;
import com.hexun.px.model.Class;
import com.hexun.px.model.ClassSection;
import com.hexun.px.model.CommentRate;
import com.hexun.px.model.OpenClass;
import com.hexun.px.model.OpenForKj;
import com.hexun.px.model.VisitNum;
import com.hexun.px.service.ClassService;
import com.hexun.task.canal.BinlogInfo;
import com.hexun.task.canal.BinlogObserver;
import com.hexun.task.canal.CanalConnectorClient;

/**
 * 课程数据同步索引客户端
 * 
 * @author xiongyan
 * @date 2017年8月4日 上午10:10:44
 */
@Component
public class LessonSyncClient implements InitializingBean, BinlogObserver {
	
	/**
	 * logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(LessonSyncClient.class);
	
	@Autowired
	private SearchClassService searchClassService;
	
	@Autowired
	private ClassService classService;
	
	@Autowired
	private CanalConnectorClient canalClient;
	
	/**
	 * bean初始化时注册观察者
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		canalClient.addObserver("lesson#px_class", this);
		canalClient.addObserver("lesson#px_class_section", this);
		canalClient.addObserver("lesson#px_open_class", this);
		canalClient.addObserver("lesson#px_open_for_kj", this);
		canalClient.addObserver("lesson#px_comment_rate", this);
		canalClient.addObserver("lesson#px_visit_num", this);
	}

	/**
	 * 消息
	 */
	@Override
	public void message(BinlogInfo binlogInfo) {
		try {
			logger.info("课程数据库日志：{}", binlogInfo);
			switch (binlogInfo.getTableName()) {
				case "px_class" :
					lesson(binlogInfo.getEventType(), binlogInfo.getData());
					break;
				case "px_class_section" :
					classSection(binlogInfo.getData());
					break;
				case "px_open_class" :
					openClass(binlogInfo.getData());
					break;
				case "px_open_for_kj" :
					openForKj(binlogInfo.getData());
					break;
				case "px_comment_rate" :
					commentRate(binlogInfo.getData());
					break;
				case "px_visit_num" :
					visitNum(binlogInfo.getData());
					break;
				default :
					break;
			}
		} catch (Exception e) {
			throw new OnlineException("日志消费失败", e);
		}
	}
	
	/**
	 * 课程实时索引
	 * 
	 * @param eventType
	 * @param data
	 * @throws InstantiationException 
	 * @throws IllegalAccessException 
	 */
	private void lesson(EventType eventType, Map<String, Object> data) throws IllegalAccessException, InstantiationException {
		Class clazz = BeanUtils.fromMap(data, Class.class);
		SearchClass searchClass = new SearchClass();
		BeanUtils.copyA2B(clazz, searchClass);
		searchClass.setId(clazz.getClassId().toString());
		searchClass.setTeacherName(UserAuth.GetNickNameByUserID(clazz.getTeacherId()));
		searchClass.setVhallPlayUrl(clazz.getPlayUrl1());
		searchClass.setGenseePlayUrl(clazz.getPlayUrl2());
		// 热门标签
		String classHotLabel = clazz.getClassHotLabel();
		if (StringUtils.isNotEmpty(classHotLabel)) {
			List<Nested> classHotLabels = new ArrayList<>();
			for (String label : StringUtils.split2Set(classHotLabel)) {
				classHotLabels.add(new Nested(label));
			}
			searchClass.setClassHotLabel(classHotLabels);
		}
		// 课程进阶
		String investMethod = clazz.getInvestMethod();
		if (StringUtils.isNotEmpty(investMethod)) {
			searchClass.setInvestMethod(classService.getInvestMethods(investMethod));
		}
		if (EventType.INSERT == eventType) {
			searchClass.setChargeType(ChargeTypeEnum.CHARGE.getValue());
			// 评论数，默认为0
			searchClass.setCommentNum(0);
			// 好评率，默认为0
			searchClass.setGoodRate(0.0);
			// 学习数，默认为0
			searchClass.setLearnNum(0);
			// 月学习数，默认为0
			searchClass.setMonthLearnNum(0);
		} 
		// 课程增量索引
		searchClassService.incrementIndex(IndexTypeEnum.LESSON, searchClass);
	}
	
	/**
	 * 更新课节的套课章节信息
	 * 
	 * @param data
	 * @throws InstantiationException 
	 * @throws IllegalAccessException 
	 */
	private void classSection(Map<String, Object> data) throws IllegalAccessException, InstantiationException {
		ClassSection classSection = BeanUtils.fromMap(data, ClassSection.class);
		Map<String, Object> source = new HashMap<>();
		if (classSection.getIsDelete()) {
			source.put(ClassIndexFieldEnum.CHAPTERID.getField(), null);
			source.put(ClassIndexFieldEnum.PACKID.getField(), null);
			source.put(ClassIndexFieldEnum.PACKCLASSID.getField(), null);
		} else {
			source.put(ClassIndexFieldEnum.CHAPTERID.getField(), classSection.getChapterId());
			source.put(ClassIndexFieldEnum.PACKID.getField(), classSection.getPackId());
			source.put(ClassIndexFieldEnum.PACKCLASSID.getField(), classSection.getPackClassId());
		}
		// 课程增量索引
		searchClassService.updateIndex(IndexTypeEnum.LESSON, classSection.getClassId().toString(), source);
	}
	
	/**
	 * 更新公开免费信息
	 * 
	 * @param data
	 * @throws InstantiationException 
	 * @throws IllegalAccessException 
	 */
	private void openClass(Map<String, Object> data) throws IllegalAccessException, InstantiationException {
		OpenClass openClass = BeanUtils.fromMap(data, OpenClass.class);
		Map<String, Object> source = new HashMap<>();
		if (openClass.getIsDelete() || !isEffectiveTime(openClass.getOpenBeginTime(), openClass.getOpenEndTime())) {
			source.put(ClassIndexFieldEnum.FREESTARTTIME.getField(), null);
			source.put(ClassIndexFieldEnum.FREEENDTIME.getField(), null);
			source.put(ClassIndexFieldEnum.OPENCLASSID.getField(), null);
			source.put(ClassIndexFieldEnum.OPENCLASSNAMESTR.getField(), null);
			source.put(ClassIndexFieldEnum.OPENCLASSDATEDESC.getField(), null);
			source.put(ClassIndexFieldEnum.OPENCLASSIMAGE.getField(), null);
			source.put(ClassIndexFieldEnum.OPENCLASSINTROSTR.getField(), null);
			source.put(ClassIndexFieldEnum.CHARGETYPE.getField(), ChargeTypeEnum.CHARGE.getValue());
		} else {
			source.put(ClassIndexFieldEnum.FREESTARTTIME.getField(), openClass.getOpenBeginTime().getTime());
			source.put(ClassIndexFieldEnum.FREEENDTIME.getField(), openClass.getOpenEndTime().getTime());
			source.put(ClassIndexFieldEnum.OPENCLASSID.getField(), openClass.getId());
			source.put(ClassIndexFieldEnum.OPENCLASSNAMESTR.getField(), openClass.getClassName());
			source.put(ClassIndexFieldEnum.OPENCLASSDATEDESC.getField(), openClass.getPlayTime());
			source.put(ClassIndexFieldEnum.OPENCLASSIMAGE.getField(), openClass.getClassPic());
			source.put(ClassIndexFieldEnum.OPENCLASSINTROSTR.getField(), openClass.getClassIntro());
			source.put(ClassIndexFieldEnum.CHARGETYPE.getField(), ChargeTypeEnum.OPEN_FREE.getValue());
		}
		// 课程增量索引
		searchClassService.updateIndex(IndexTypeEnum.LESSON, openClass.getClassId().toString(), source);
	}
	
	/**
	 * 更新限时免费信息
	 * 
	 * @param data
	 * @throws InstantiationException 
	 * @throws IllegalAccessException 
	 */
	private void openForKj(Map<String, Object> data) throws IllegalAccessException, InstantiationException {
		OpenForKj openForKj = BeanUtils.fromMap(data, OpenForKj.class);
		Map<String, Object> source = new HashMap<>();
		if (openForKj.getIsDelete() || !isEffectiveTime(openForKj.getOpenStartDate(), openForKj.getOpenEndDate())) {
			source.put(ClassIndexFieldEnum.FREESTARTTIME.getField(), null);
			source.put(ClassIndexFieldEnum.FREEENDTIME.getField(), null);
			source.put(ClassIndexFieldEnum.CHARGETYPE.getField(), ChargeTypeEnum.CHARGE.getValue());
		} else {
			source.put(ClassIndexFieldEnum.FREESTARTTIME.getField(), openForKj.getOpenStartDate().getTime());
			source.put(ClassIndexFieldEnum.FREEENDTIME.getField(), openForKj.getOpenEndDate().getTime());
			source.put(ClassIndexFieldEnum.CHARGETYPE.getField(), ChargeTypeEnum.TIME_FREE.getValue());
		}
		// 课程增量索引
		searchClassService.updateIndex(IndexTypeEnum.LESSON, openForKj.getClassId().toString(), source);
	}
	
	/**
	 * 是否有效时间
	 * 
	 * @param startTime
	 * @param endTime
	 * @return
	 */
	private boolean isEffectiveTime(Date startTime, Date endTime) {
		Date now = new Date();
		return startTime.before(now) && endTime.after(now);
	}
	
	/**
	 * 更新评论数和评论率
	 * 
	 * @param data
	 * @throws InstantiationException 
	 * @throws IllegalAccessException 
	 */
	private void commentRate(Map<String, Object> data) throws IllegalAccessException, InstantiationException {
		CommentRate commentRate = BeanUtils.fromMap(data, CommentRate.class);
		Map<String, Object> source = new HashMap<>();
		source.put(ClassIndexFieldEnum.COMMENTNUM.getField(), commentRate.getGoodCommentNum() + commentRate.getOkCommentNum() + commentRate.getBadCommentNum());
		source.put(ClassIndexFieldEnum.GOODRATE.getField(), commentRate.getGoodCommentRate().doubleValue());
		// 课程增量索引
		searchClassService.updateIndex(IndexTypeEnum.LESSON, commentRate.getClassId().toString(), source);
	}
	
	/**
	 * 更新学习数和月学习数
	 * 
	 * @param data
	 * @throws InstantiationException 
	 * @throws IllegalAccessException 
	 */
	private void visitNum(Map<String, Object> data) throws IllegalAccessException, InstantiationException {
		VisitNum visitNum = BeanUtils.fromMap(data, VisitNum.class);
		Map<String, Object> source = new HashMap<>();
		source.put(ClassIndexFieldEnum.LEARNNUM.getField(), visitNum.getVisitNum());
		source.put(ClassIndexFieldEnum.MONTHLEARNNUM.getField(), visitNum.getMonthVisitNum());
		// 课程增量索引
		searchClassService.updateIndex(IndexTypeEnum.LESSON, visitNum.getClassId().toString(), source);
	}
}
