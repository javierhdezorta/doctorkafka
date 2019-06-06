package com.pinterest.doctorkafka.modules.action.cluster.kafka;

import com.pinterest.doctorkafka.modules.action.Action;
import com.pinterest.doctorkafka.modules.errors.ModuleConfigurationException;
import com.pinterest.doctorkafka.modules.event.Event;
import com.pinterest.doctorkafka.modules.event.NotificationEvent;
import com.pinterest.doctorkafka.modules.event.ReportEvent;
import com.pinterest.doctorkafka.util.KafkaUtils;

import kafka.utils.ZkUtils;
import org.apache.commons.configuration2.AbstractConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.data.ACL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ZkUtilReassignPartition extends Action {
  private static final Logger LOG = LogManager.getLogger(ZkUtilReassignPartition.class);

  private static final String EVENT_REPORT_OPERATION_NAME = "report_operation";
  private static final String EVENT_NOTIFY_REASSIGNMENT_NAME = "notify_reassignment";

  private static final String EVENT_ZKURL_KEY = "zkurl";
  private static final String EVENT_CLUSTER_NAME_KEY = "cluster_name";
  private static final String EVENT_JSON_REASSIGNMENT_KEY = "json_reassignment";

  private final static String DEFAULT_EVENT_SUBJECT = "n/a";

  @Override
  public void configure(AbstractConfiguration config) throws ModuleConfigurationException {
    super.configure(config);
  }

  @Override
  public Collection<Event> execute(Event event) throws Exception {
    if(isDryRun()){
      LOG.info("Dry run: Action {} triggered by event {}", this.getClass(), event.getName());
    } else if(event.containsAttribute(EVENT_ZKURL_KEY) && event.containsAttribute(EVENT_JSON_REASSIGNMENT_KEY)) {
      String clusterName = event.containsAttribute(EVENT_CLUSTER_NAME_KEY) ?
                           (String) event.getAttribute(EVENT_CLUSTER_NAME_KEY) :
                           DEFAULT_EVENT_SUBJECT;
      String zkUrl = (String) event.getAttribute(EVENT_ZKURL_KEY);
      String jsonReassignment= (String) event.getAttribute(EVENT_JSON_REASSIGNMENT_KEY);
      return reassign(zkUrl, clusterName, jsonReassignment);
    }
    return null;
  }

  protected Collection<Event> reassign(String zkUrl, String clusterName, String jsonReassignment) throws Exception {
    ZkUtils zkUtils = KafkaUtils.getZkUtils(zkUrl);
    Collection<Event> events = null;
    if (zkUtils.pathExists(KafkaUtils.ReassignPartitionsPath)) {
      LOG.warn("Existing reassignment on zookeeper: {}", zkUrl);
    } else {
      List<ACL> acls = KafkaUtils.getZookeeperAcls(false);
      zkUtils.createPersistentPath(KafkaUtils.ReassignPartitionsPath, jsonReassignment, acls);
      events = createReassignedEvents(clusterName, jsonReassignment);
    }
    return events;
  }

  protected Collection<Event> createReassignedEvents(String clusterName, String jsonReassignment){
    Collection<Event> events = new ArrayList<>();
    events.add(new ReportEvent(EVENT_REPORT_OPERATION_NAME, clusterName, "Reassigning partitions: " + jsonReassignment));

    String title = clusterName + "partition reassignment";
    String message = "Assignment json: \n\n" + jsonReassignment;
    events.add(new NotificationEvent(EVENT_NOTIFY_REASSIGNMENT_NAME, title , message));
    return events;
  }
}
