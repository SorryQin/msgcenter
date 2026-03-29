package cn.sorryqin.msgcenter.manager;

import cn.sorryqin.msgcenter.enums.ChannelEnum;
import cn.sorryqin.msgcenter.enums.MsgStatus;
import cn.sorryqin.msgcenter.mapper.MsgRecordMapper;
import cn.sorryqin.msgcenter.mapper.TemplateMapper;
import cn.sorryqin.msgcenter.model.MsgRecordModel;
import cn.sorryqin.msgcenter.model.TemplateModel;
import cn.sorryqin.msgcenter.model.dto.SendMsgReq;
import cn.sorryqin.msgcenter.msgpush.MsgPushService;
import cn.sorryqin.msgcenter.msgpush.base.ChannelMsgBase;
import cn.sorryqin.msgcenter.msgpush.channel.EmailServiceImpl;
import cn.sorryqin.msgcenter.msgpush.channel.LarkServiceImpl;
import cn.sorryqin.msgcenter.msgpush.channel.SMSServiceImpl;
import cn.sorryqin.msgcenter.service.TemplateService;
import cn.sorryqin.msgcenter.tools.MsgRecordService;
import cn.sorryqin.msgcenter.utils.JSONUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
public class DealMsgManagerImpl implements DealMsgManager{

    private static final Logger log = LoggerFactory.getLogger(DealMsgManagerImpl.class);

    public static Map<Integer, MsgPushService> channelStrategyMap = new HashMap<>();

    @Autowired
    MsgPushService emailServiceImpl;
    @Autowired
    MsgPushService larkServiceImpl;
    @Autowired
    MsgPushService SMSServiceImpl;

    // 初始化各种推送策略服务 Email|Lark|SMS
    @PostConstruct
    public void initChannelStrategyMap() {
        channelStrategyMap.put(ChannelEnum.Channel_EMAIL.getChannel(), emailServiceImpl);
        channelStrategyMap.put(ChannelEnum.Channel_LARK.getChannel(), larkServiceImpl);
        channelStrategyMap.put(ChannelEnum.Channel_SMS.getChannel(), SMSServiceImpl);
    }
    @Autowired
    TemplateService templateService;

    @Autowired
    MsgRecordService msgRecordService;

    @Override
    public void DealOneMsg(SendMsgReq sendMsgReq) {

        // 1. 查找模板
        TemplateModel tp = templateService.GetTemplateWithCache(sendMsgReq.getTemplateId());

        // 2.替换模板中的变量
        String msgContent = replaceStr(tp.getContent(),sendMsgReq.getTemplateData());

        // 3. 构建推送消息的基本参数
        ChannelMsgBase base = new ChannelMsgBase();
        base.setTo(sendMsgReq.getTo());
        base.setSubject(sendMsgReq.getSubject());
        base.setContent(msgContent);
        base.setPriority(sendMsgReq.getPriority());
        base.setTemplateId(sendMsgReq.getTemplateId());
        base.setTemplateData(sendMsgReq.getTemplateData());

        // 4. 根据渠道，获取具体的推送策略 Email|Lark|SMS
        MsgPushService msgService = channelStrategyMap.get(tp.getChannel());

        // 5. 调用具体策略服务去推送消息
        msgService.pushMsg(base);

        // 6. 存储消息发送记录
        try{
            msgRecordService.CreateOrUpdateMsgRecord(sendMsgReq.getMsgID(),sendMsgReq,tp, MsgStatus.Succeed);
        }catch (Exception e){
            log.error("存储消息发送记录失败， msgId",sendMsgReq.getMsgID());
        }

    }

    private String replaceStr(String template,Map<String,String> paramsMap) {
        String remark = template;
        for (Map.Entry<String, String> entry : paramsMap.entrySet()) {
            remark = StringUtils.replace(remark, "${" + entry.getKey() + "}", entry.getValue());
        }
        return remark;
    }
}
