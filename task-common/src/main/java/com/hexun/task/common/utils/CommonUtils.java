package com.hexun.task.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import com.hexun.common.utils.ListPageUtil;
import com.hexun.hwcommon.service.UserAuth;

import net.sf.cglib.beans.BeanMap;

/**
 * 工具类
 * 
 * @author xiongyan
 * @date 2017年5月13日 上午10:55:41
 */
public final class CommonUtils {
	
	/**
	 * 正则表达式
	 */
	private static final Pattern LINEPATTERN = Pattern.compile("_(\\w)");
	
	/**
	 * 字段map
	 */
	private static final Map<String, String> FIELDMAP = new HashMap<>();
	
	private CommonUtils() {
		
	}
	
	/**
	 * 获取老师昵称
	 * 
	 * @param list
	 * @param fieldName
	 * @return
	 */
	public static <T> Map<Long, String> teacherMap(List<T> list, String fieldName) {
		// 获取老师id
		Set<Object> teacherIds = fieldValue(list, fieldName);
		
		// 分页查询老师昵称
		Map<Long, String> teacherMap = new HashMap<>();
		List<List<Object>> pages = ListPageUtil.listPage(new ArrayList<>(teacherIds), 200);
		for (List<Object> page : pages) {
			Map<Long, String> nickNameMap = UserAuth.getNickNameByUserIDs(StringUtils.join(page.toArray(), ","));
			if (MapUtils.isNotEmpty(nickNameMap)) {
				teacherMap.putAll(nickNameMap);
			}
		}
		return teacherMap;
	}
	
	/**
	 * 获取属性值
	 * 
	 * @param list
	 * @param fieldName
	 * @return
	 */
	public static <T> Set<Object> fieldValue(List<T> list, String fieldName) {
		Set<Object> teacherIds = new HashSet<>();
		for (T t : list) {
			BeanMap beanMap = BeanMap.create(t);
			Object value = beanMap.get(fieldName);
			if (null != value) {
				teacherIds.add(value);
			}
		}
		return teacherIds;
	}
	
	
	/** 
	 * 下划线转驼峰
	 * 
	 * @param lineName
	 * @return 
	 */
	public static String lineToHump(String lineName) {
		String humpName = FIELDMAP.get(lineName);
		if (StringUtils.isNotEmpty(humpName)) {
			return humpName;
		}
		
		Matcher matcher = LINEPATTERN.matcher(lineName);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(sb, matcher.group(1).toUpperCase());
		}
		matcher.appendTail(sb);
		humpName = sb.toString();
		FIELDMAP.put(lineName, humpName);
		return humpName;
	}
	
	/**
	 * 去掉html标签
	 * 
	 * @param str
	 * @return
	 */
	public static String removeHtml(String str) {
		if (StringUtils.isEmpty(str)) {
			return StringUtils.EMPTY;
		}
		return str.replaceAll("<[^>]*>| |　|&nbsp;", StringUtils.EMPTY);
	}
	
}
