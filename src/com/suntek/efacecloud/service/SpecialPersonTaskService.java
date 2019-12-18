package com.suntek.efacecloud.service;

import com.suntek.eap.EAP;
import com.suntek.eap.core.app.AppHandle;
import com.suntek.eap.index.Query;
import com.suntek.eap.jdbc.PageQueryResult;
import com.suntek.eap.log.ServiceLog;
import com.suntek.eap.pico.annotation.BeanService;
import com.suntek.eap.pico.annotation.LocalComponent;
import com.suntek.eap.util.DateUtil;
import com.suntek.eap.util.StringUtil;
import com.suntek.eap.web.RequestContext;
import com.suntek.efacecloud.dao.FaceCommonDao;
import com.suntek.efacecloud.dao.SpecialPersonTrackDao;
import com.suntek.efacecloud.log.Log;
import com.suntek.efacecloud.util.Constants;
import com.suntek.efacecloud.util.ESUtil;
import com.suntek.efacecloud.util.ModuleUtil;
import com.suntek.efacecloud.util.SpecialTarckLibType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author: LiLing
 * @create: 2019-10-30 19:46:01
 */
@LocalComponent(id = "technicalTactics/task")
public class SpecialPersonTaskService {
    private static SpecialPersonTrackDao dao = new SpecialPersonTrackDao();
    private static FaceCommonDao commonDao = new FaceCommonDao();
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @BeanService(id = "add", description = "添加特定人群轨迹分析任务", since = "1.0.0", type = "remote")
    public void add(RequestContext context) throws Exception {
        try {
            // 前端加非空判断
            String taskName = StringUtil.toString(context.getParameter("TASK_NAME"));
            String taskType = StringUtil.toString(context.getParameter("TASK_TYPE")); //1-人流量分析; 2-特定人群轨迹
            String beginTime = StringUtil.toString(context.getParameter("BEGIN_TIME"));
            String endTime = StringUtil.toString(context.getParameter("END_TIME"));
            String deviceIds = StringUtil.toString(context.getParameter("DEVICE_IDS"));
            String deviceNames = StringUtil.toString(context.getParameter("DEVICE_NAMES"));
            String topN = StringUtil.toString(context.getParameter("TOP_N"));
            String threshold = StringUtil.toString(context.getParameter("THRESHOLD"), "70");
            String faceScore = StringUtil.toString(context.getParameter("FACE_SCORE"), "65"); //特征分数
            String taskStatus = StringUtil.toString(context.getParameter("TASK_STATUS"), "0");
            String libType = StringUtil.toString(context.getParameter("LIB_TYPE"));
            String libId = StringUtil.toString(context.getParameter("LIB_ID"));
            String libName = StringUtil.toString(context.getParameter("LIB_NAME"));
            String taskId = EAP.keyTool.getUUID();
            String userCode = context.getUserCode();
            if (StringUtil.isEmpty(userCode)) {
                context.getResponse().putData("CODE", 1);
                context.getResponse().putData("MESSAGE", "请先登录!");
                return;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("TASK_ID", taskId);
            map.put("TASK_NAME", taskName);
            map.put("TASK_TYPE", taskType);
            map.put("BEGIN_TIME", beginTime);
            map.put("END_TIME", endTime);
            if (!StringUtil.isEmpty(topN)) {
                map.put("TOP_N", Integer.parseInt(topN));
            } else {
                map.put("TOP_N", null);
            }
            map.put("THRESHOLD", threshold);
            if (!StringUtil.isEmpty(faceScore)) {
                map.put("FACE_SCORE", Integer.parseInt(faceScore));
            } else {
                map.put("FACE_SCORE", null);
            }

            map.put("DEVICE_IDS", deviceIds);
            map.put("DEVICE_NAMES", deviceNames);
            map.put("TASK_STATUS", taskStatus);  //0-待办; 1-进行中; 2-已完成; 3-任务异常
            map.put("LIB_TYPE", libType);
            if (!StringUtil.isEmpty(libType)) {
                map.put("LIB_TYPE", Integer.parseInt(libType));
                map.put("LIB_TYPE_DESC", SpecialTarckLibType.getName(Integer.parseInt(libType)));
                map.put("LIB_ID", libId);
                map.put("LIB_NAME", libName);
            } else {
                map.put("LIB_TYPE", null);
                map.put("LIB_TYPE_DESC", null);
                map.put("LIB_ID", null);
                map.put("LIB_NAME", null);
            }
            map.put("IS_TOP", 0);  // 是否置顶
            map.put("CREATE_TIME", sdf.format(new Date()));
            map.put("CREATOR", userCode);
            if (!StringUtil.isEmpty(taskType) && "1".equals(taskType)) {
                // 控制检索数量不大于100万
                long capNum = getCapNum(beginTime, endTime, deviceIds, faceScore);
                ServiceLog.debug("人流量分析任务检索数量 : " + capNum);
                int faceN2NThreshold = Integer.parseInt(AppHandle.getHandle(Constants.APP_NAME)
                        .getProperty("FACE_N2N_THRESHOLD", "1000000"));
                if (faceN2NThreshold < capNum) {
                    context.getResponse().setWarn("检索范围过大, 请减少设备或缩短检索时间范围!");
                    return;
                }
            }
            dao.addTask(map);
            context.getResponse().putData("CODE", 0);
            context.getResponse().putData("MESSAGE", "添加任务成功");
            context.getResponse().putData("TASK_ID", taskId);
        } catch (Exception e) {
            ServiceLog.error("添加特定人群轨迹分析任务异常 : " + e.getMessage());
            context.getResponse().putData("CODE", 1);
            context.getResponse().putData("MESSAGE", "添加任务失败");
        }
    }

    @BeanService(id = "update", description = "更新特定人群轨迹分析任务", since = "1.0.0", type = "remote")
    public void update(RequestContext context) throws Exception {
        try {
            // 前端加非空判断
            String isTop = StringUtil.toString(context.getParameter("IS_TOP")); //1-置顶
            String finishTime = StringUtil.toString(context.getParameter("FINISH_TIME"));
            String taskId = StringUtil.toString(context.getParameter("TASK_ID"));
            if (StringUtil.isEmpty(taskId)) {
                context.getResponse().putData("CODE", 1);
                context.getResponse().putData("MESSAGE", "请传入必须参数值!");
                return;
            }
            if (!StringUtil.isEmpty(isTop)) {
                dao.updateByIstop(taskId, isTop);
            }
            if (!StringUtil.isEmpty(isTop)) {
                dao.updateByFinishTime(taskId, finishTime);
            }

            context.getResponse().putData("CODE", 0);
            context.getResponse().putData("MESSAGE", "更新成功");

        } catch (Exception e) {
            ServiceLog.error("更新特定人群轨迹分析任务异常 : " + e.getMessage());
            context.getResponse().putData("CODE", 1);
            context.getResponse().putData("MESSAGE", "更新失败");
        }
    }

    @BeanService(id = "detail", description = "查询特定人群轨迹分析任务详情", since = "1.0.0", type = "remote")
    public void queryDetail(RequestContext context) throws Exception {
        try {
            String taskId = StringUtil.toString(context.getParameter("TASK_ID"));
            List<Map<String, Object>> list = dao.queryTaskDetail(taskId);
            for (Map<String, Object> map : list) {
                String taskType = com.suntek.eap.common.util.StringUtil.toString(map.get("TASK_TYPE"));
                if ("1".equals(taskType)) {
                    map.put("TASK_TYPE", "人脸聚档(人流量估计分析)");
                } else if ("2".equals(taskType)) {
                    map.put("TASK_TYPE", "特定人群轨迹分析");
                }
            }
            context.getResponse().putData("CODE", 0);
            context.getResponse().putData("MESSAGE", "查询成功");
            context.getResponse().putData("DATA", list);
        } catch (Exception e) {
            ServiceLog.error("查询特定人群轨迹分析任务详情异常 : " + e.getMessage());
            context.getResponse().putData("CODE", 1);
            context.getResponse().putData("MESSAGE", "查询失败");
        }
    }

    @BeanService(id = "delete", description = "删除特定人群轨迹分析任务", since = "1.0.0", type = "remote")
    public void delete(RequestContext context) throws Exception {
        try {
            // 暂时不做逻辑删除
            String taskIds = StringUtil.toString(context.getParameter("TASK_IDS"));
            dao.deleteTask(taskIds);
            context.getResponse().putData("CODE", 0);
            context.getResponse().putData("MESSAGE", "删除成功");
        } catch (Exception e) {
            ServiceLog.error("查询特定人群轨迹分析任务异常 : " + e.getMessage());
            context.getResponse().putData("CODE", 1);
            context.getResponse().putData("MESSAGE", "删除失败");
        }
    }

    /**
     * 查询任务关联有轨迹结果的档案
     *
     * @param context
     * @throws Exception
     */
    @BeanService(id = "queryTrackResult", description = "查询特定人群轨迹分析任务结果", since = "1.0.0", type = "remote")
    public void queryTrackResult(RequestContext context) throws Exception {
        try {
            String taskId = StringUtil.toString(context.getParameter("TASK_ID"));
            List<Map<String, Object>> list = dao.queryResult(taskId);
            for (Map<String, Object> map : list) {
                String personTags = StringUtil.toString(map.get("PERSON_TAG"));
                String[] tags = personTags.replaceAll("  ", " ").split(" ");
                Map<Object, Object> personTagMap = commonDao.getPersonTagInfo().stream().collect(Collectors.toMap(o -> o.get("CODE"), o -> o.get("NAME")));
                Set<String> tempSet = new LinkedHashSet<String>();
                List<Map<String, Object>> tagList = new ArrayList<>();
                for (int i = 0; i < tags.length; i++) {
                    Map<String, Object> tagMap = new HashMap<>();
                    String personTagName = StringUtil.toString(personTagMap.get(tags[i]));
                    if (!StringUtil.isNull(personTagName) && tags[i].length() > 2) {
                        tempSet.add(personTagName);
                        tagMap.put("TAG_CODE", tags[i]);
                        tagMap.put("TAG_NAME", personTagName);
                        tagList.add(tagMap);
                    }
                }
                map.put("PERSON_TAG_NAME", tempSet.toString().replaceAll(" ", ""));
                map.put("PERSON_TAG_LIST", tagList);
                String pic = StringUtil.toString(map.get("PIC"));
                if (!StringUtil.isEmpty(pic)) {
                    map.put("PIC", ModuleUtil.renderPic(ModuleUtil.renderImage(pic)));
                }
            }
            context.getResponse().putData("CODE", 0);
            context.getResponse().putData("MESSAGE", "查询成功");
            context.getResponse().putData("DATA", list);
        } catch (Exception e) {
            ServiceLog.error("查询特定人群轨迹分析任务详情异常 : " + e.getMessage());
            context.getResponse().putData("CODE", 1);
            context.getResponse().putData("MESSAGE", "查询失败");
        }
    }

    @BeanService(id = "queryPersonFlow", description = "查询人流量分析任务结果", since = "1.0.0", type = "remote")
    public void queryPersonFlow(RequestContext context) throws Exception {
        try {
            String taskId = StringUtil.toString(context.getParameter("TASK_ID"));
            List<Map<String, Object>> list = dao.queryPersonFlow(taskId);
            context.getResponse().putData("CODE", 0);
            context.getResponse().putData("MESSAGE", "查询成功");
            if (list.size() > 0) {
                context.getResponse().putData("COUNT", list.get(0).get("PERSON_FLOW"));
            } else {
                context.getResponse().putData("COUNT", 0);
            }

        } catch (Exception e) {
            ServiceLog.error("查询特定人群轨迹分析任务详情异常 : " + e.getMessage());
            context.getResponse().putData("CODE", 1);
            context.getResponse().putData("MESSAGE", "查询失败");
        }
    }

    @BeanService(id = "queryException", description = "查询特定人群轨迹分析任务异常原因", since = "1.0.0", type = "remote")
    public void queryException(RequestContext context) throws Exception {
        try {
            String taskId = StringUtil.toString(context.getParameter("TASK_ID"));
            Map<String, Object> map = dao.queryTaskException(taskId);
            context.getResponse().putData("CODE", 0);
            context.getResponse().putData("MESSAGE", "查询成功");
            context.getResponse().putData("DATA", StringUtil.toString(map.get("EXP_REASON")));
        } catch (Exception e) {
            ServiceLog.error("查询特定人群轨迹分析任务异常原因异常 : " + e.getMessage());
            context.getResponse().putData("CODE", 1);
            context.getResponse().putData("MESSAGE", "查询失败");
        }
    }

    private long getCapNum(String beginTime, String endTime, String deviceIds, String faceScore) {
        long captureNum = 0;
        String[] indices = ESUtil.getIndicesNameByBeginAndEndTime(Constants.FACE_INDEX, beginTime, endTime);
        try {
            Query query = new Query(1, 1);
            String[] deviceIdArr = deviceIds.split(",");
            List<String> deviceIdList = new ArrayList<String>();
            for (String deviceId : deviceIdArr) {
                deviceIdList.add(deviceId);
            }
            query.addEqualCriteria("DEVICE_ID", deviceIdList.toArray());
            Long sjgsk = Long.valueOf(
                    com.suntek.efacecloud.util.DateUtil.convertByStyle(beginTime, com.suntek.efacecloud.util.DateUtil.standard_style, com.suntek.efacecloud.util.DateUtil.yyMMddHHmmss_style, "-1"));
            Long ejgsk = Long.valueOf(
                    com.suntek.efacecloud.util.DateUtil.convertByStyle(endTime, com.suntek.efacecloud.util.DateUtil.standard_style, com.suntek.efacecloud.util.DateUtil.yyMMddHHmmss_style, "-1"));
            query.addBetweenCriteria("JGSK", sjgsk, ejgsk);
            PageQueryResult pageResult = EAP.bigdata.query(indices, Constants.FACE_TABLE, query);
            if (!pageResult.getResultSet().isEmpty()) {
                captureNum = StringUtil.toLong(pageResult.getTotalSize(), 0);
            }
        } catch (Exception e) {
        }
        return captureNum;
    }

    @BeanService(id = "testFloat", type = "remote")
    public void test(RequestContext context) {
        String deviceIds = StringUtil.toString(context.getParameter("DEVICE_IDS"));
        String faceScore = StringUtil.toString(context.getParameter("FACE_SCORE"));
        String beginTime = StringUtil.toString(context.getParameter("BEGIN_TIME"));
        String endTime = StringUtil.toString(context.getParameter("END_TIME"));
        long captureNum = 0;
        String[] indices = ESUtil.getIndicesNameByBeginAndEndTime(Constants.FACE_INDEX, beginTime, endTime);
        Log.tasklog.debug("indices : " + Arrays.toString(indices));
        try {
            Query query = new Query(1, 1500000);
            String[] deviceIdArr = deviceIds.split(",");
            List<String> deviceIdList = new ArrayList<String>();
            for (String deviceId : deviceIdArr) {
                deviceIdList.add(deviceId);
            }
            query.addEqualCriteria("DEVICE_ID", deviceIdList.toArray());
            Long sjgsk = Long.valueOf(
                    DateUtil.convertByStyle(beginTime, DateUtil.standard_style, DateUtil.yyMMddHHmmss_style, "-1"));
            Long ejgsk = Long.valueOf(
                    DateUtil.convertByStyle(endTime, DateUtil.standard_style, DateUtil.yyMMddHHmmss_style, "-1"));
            query.addBetweenCriteria("JGSK", sjgsk, ejgsk);
            PageQueryResult pageResult = EAP.bigdata.query(indices, Constants.FACE_TABLE, query);
            Log.tasklog.debug("result: " + pageResult.getResultSet().size());
            if (!pageResult.getResultSet().isEmpty()) {
                captureNum = StringUtil.toLong(pageResult.getTotalSize(), 0);
            }
            List<Map<String, Object>> list = pageResult.getResultSet();
            if (!StringUtil.isEmpty(faceScore)) {
                int face = Integer.parseInt(faceScore);
                list = list.stream().filter(o -> {
                    Integer faceS = Integer.parseInt(StringUtil.toString(o.get("FACE_SCORE"), "0"));
                    Log.tasklog.debug("faceS: " + faceS);
                    if (faceS > face) {
                        return true;
                    }
                    return false;
                }).collect(Collectors.toList());
                Log.tasklog.debug("face : " + face);
                context.getResponse().putData("total ", list.size());
            } else {
                context.getResponse().putData("total ", captureNum);
            }
        } catch (Exception e) {
            Log.tasklog.debug("反查es异常: " + e.getMessage());
            context.getResponse().putData("total ", e.getMessage());
        }
    }
}
