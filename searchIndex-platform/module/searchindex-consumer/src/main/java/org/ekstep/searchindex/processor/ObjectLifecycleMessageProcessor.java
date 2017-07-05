package org.ekstep.searchindex.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.ekstep.learning.util.ControllerUtil;
import org.ekstep.searchindex.enums.ConsumerWorkflowEnums;

import com.ilimi.common.dto.Response;
import com.ilimi.common.util.ILogger;
import com.ilimi.common.util.LogTelemetryEventUtil;
import com.ilimi.common.util.PlatformLogger;
import com.ilimi.graph.dac.model.Node;
import com.ilimi.graph.dac.model.Relation;

/**
 * The Class ObjectLifecycleMessageProcessor is a kafka consumer which provides
 * implementations of the object lifecycle operations defined in the
 * IMessageProcessor along with the methods to generate lifecycle event for all
 * platform objects
 * 
 * @author Rashmi
 * 
 * @see IMessageProcessor
 */
public class ObjectLifecycleMessageProcessor implements IMessageProcessor {

	/** The LOGGER */
	private static ILogger LOGGER = new PlatformLogger(ObjectLifecycleMessageProcessor.class.getName());

	/** The ObjectMapper */
	private ObjectMapper mapper = new ObjectMapper();

	/** The controllerUtil */
	private ControllerUtil util = new ControllerUtil();

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.ekstep.searchindex.processor #processMessage(java.lang.String,
	 * java.lang.String, java.io.File, java.lang.String)
	 */
	@Override
	public void processMessage(String messageData) {
		try {
			Map<String, Object> message = new HashMap<String, Object>();
			if (StringUtils.isNotBlank(messageData)) {
				LOGGER.log("Reading from kafka consumer" + messageData);
				message = mapper.readValue(messageData, new TypeReference<Map<String, Object>>() {
				});
			}
			if (null != message)
				processMessage(message);
		} catch (Exception e) {
			LOGGER.log("Error while processing kafka message", e.getMessage(), e);
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.ekstep.searchindex.processor #processMessage(java.lang.String
	 * java.lang.String, java.io.File, java.lang.String)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void processMessage(Map<String, Object> message) throws Exception {
		Map<String, Object> objectMap = new HashMap<String, Object>();
		try {
			LOGGER.log("Checking if kafka message contains transactionData"
					+ message.containsKey(ConsumerWorkflowEnums.transactionData.name()));
			if (message.containsKey(ConsumerWorkflowEnums.transactionData.name())) {
				Map<String, Object> transactionMap = (Map<String, Object>) message
						.get(ConsumerWorkflowEnums.transactionData.name());

				LOGGER.log("Checking tarnsactionData contains propertiesMap"
						+ transactionMap.containsKey(ConsumerWorkflowEnums.properties.name()));
				if (transactionMap.containsKey(ConsumerWorkflowEnums.properties.name())) {
					Map<String, Object> propertiesMap = (Map<String, Object>) transactionMap
							.get(ConsumerWorkflowEnums.properties.name());

					LOGGER.log("Checking if propertiesMap contains status"
							+ propertiesMap.containsKey(ConsumerWorkflowEnums.status.name()));
					if (propertiesMap.containsKey(ConsumerWorkflowEnums.status.name())) {
						Map<String, Object> statusMap = (Map) propertiesMap.get(ConsumerWorkflowEnums.status.name());

						LOGGER.log("Setting prevState and current state for event generation");
						String prevstate = (String) statusMap.get("ov");
						String state = (String) statusMap.get("nv");

						LOGGER.log("Checking if node_id is blank"
								+ message.get(ConsumerWorkflowEnums.nodeUniqueId.name()));
						if (StringUtils.isNotBlank((String) message.get(ConsumerWorkflowEnums.nodeUniqueId.name()))) {
							Node node = new Node();
							if (message.get(ConsumerWorkflowEnums.nodeType.name())
									.equals(ConsumerWorkflowEnums.SET.name())
									&& message.get(ConsumerWorkflowEnums.objectType.name())
											.equals(ConsumerWorkflowEnums.ItemSet.name())) {
								if (null != message.get(ConsumerWorkflowEnums.nodeUniqueId.name())) {
									String node_id = (String) message.get(ConsumerWorkflowEnums.nodeUniqueId.name());
									LOGGER.log("Getting Itemset from graph via rest call" + node_id);
									node = getItemSetNode(node_id);
								}
							} else {
								LOGGER.log("Fetching Node metadata from graph"
										+ message.get(ConsumerWorkflowEnums.nodeUniqueId.name()));
								node = util.getNode(ConsumerWorkflowEnums.domain.name(),
										(String) message.get(ConsumerWorkflowEnums.nodeUniqueId.name()));
							}
							String node_id = node.getIdentifier();
							String objectType = node.getObjectType();
							
							LOGGER.log("Checking if prevState is null "+ prevstate);
							if (null == prevstate) {
								objectMap.put(ConsumerWorkflowEnums.prevstate.name(), "");
							} else {
								objectMap.put(ConsumerWorkflowEnums.prevstate.name(), prevstate);
							}
							objectMap.put(ConsumerWorkflowEnums.state.name(), state);
							
							
							LOGGER.log("prevstate of object:" + prevstate + "currentstate of object:" + state + objectType);
							if (StringUtils.equalsIgnoreCase(objectType, ConsumerWorkflowEnums.ContentImage.name())
									&& StringUtils.equalsIgnoreCase(prevstate, null)
									&& StringUtils.equalsIgnoreCase(state, ConsumerWorkflowEnums.Draft.name())) {
								LOGGER.log("Setting status for content Image created" + prevstate + state);
								objectMap.put(ConsumerWorkflowEnums.prevstate.name(),
										ConsumerWorkflowEnums.Live.name());
								objectMap.put(ConsumerWorkflowEnums.state.name(),
										ConsumerWorkflowEnums.Draft.name());
							} else if (StringUtils.equalsIgnoreCase(objectType, ConsumerWorkflowEnums.ContentImage.name())
									&& StringUtils.equalsIgnoreCase(prevstate, null)
									&& StringUtils.equalsIgnoreCase(state, ConsumerWorkflowEnums.FlagDraft.name())){
								LOGGER.log("Setting status for content Flag Image created" + prevstate + state);
									objectMap.put(ConsumerWorkflowEnums.prevstate.name(),
											ConsumerWorkflowEnums.Flagged.name());
									objectMap.put(ConsumerWorkflowEnums.state.name(),
											ConsumerWorkflowEnums.FlagDraft.name());
							}
							
							if (StringUtils.endsWithIgnoreCase(node_id, ".img")
									&& StringUtils.endsWithIgnoreCase(objectType, ConsumerWorkflowEnums.Image.name())) {
								LOGGER.log("Setting nodeId and objectType" + node_id + objectType);
								node_id = StringUtils.replace(node_id, ".img", "");
								objectType = StringUtils.replace(objectType, ConsumerWorkflowEnums.Image.name(), "");
							}
							objectMap.put(ConsumerWorkflowEnums.identifier.name(), node_id);
							objectMap.put(ConsumerWorkflowEnums.objectType.name(), objectType);
							LOGGER.log("Object Map" , objectMap);
							LOGGER.log("Checking if node metadata is null");
							if (null != node.getMetadata()) {
								Map<String, Object> nodeMap = new HashMap<String, Object>();
								nodeMap = (Map) node.getMetadata();
								if (nodeMap.containsKey(ConsumerWorkflowEnums.name.name())) {
									objectMap.put(ConsumerWorkflowEnums.name.name(),
											nodeMap.get(ConsumerWorkflowEnums.name.name()));
								} else {
									objectMap.put(ConsumerWorkflowEnums.name.name(), "");
								}
								if (nodeMap.containsKey(ConsumerWorkflowEnums.code.name())) {
									objectMap.put(ConsumerWorkflowEnums.code.name(),
											nodeMap.get(ConsumerWorkflowEnums.code.name()));
								} else {
									objectMap.put(ConsumerWorkflowEnums.code.name(), "");
								}
							}
							switch (objectType) {
							case "Content":
								setContentMetadata(node, objectMap);
								break;
							case "AssessmentItem":
								setItemMetadata(node, objectMap);
								break;
							case "ItemSet":
								setItemSetMetadata(node, objectMap);
								break;
							case "Concept":
								setConceptMetadata(node, objectMap);
								break;
							case "Dimension":
								setDimensionMetadata(node, objectMap);
								break;
							default:
								setDefaultMetadata(node, objectMap);
								break;
							}
							LOGGER.log("Logging Telemetry for BE_OBJECT_LIFECYCLE event" + node_id + objectMap);
							LogTelemetryEventUtil.logObjectLifecycleEvent(node_id, objectMap);
						}
					}
				}
			}
		} catch (Exception e) {
			LOGGER.log("Something occured while processing request to generate lifecycle event", e.getMessage(), e);
		}
	}

	/**
	 * This method holds logic to getItemSet NOde from graph
	 * 
	 * @param identifier
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Node getItemSetNode(String identifier) throws Exception {
		ControllerUtil util = new ControllerUtil();
		Response resp = util.getSet(ConsumerWorkflowEnums.domain.name(), identifier);
		Map<String, Object> map = (Map) resp.getResult();
		Node node = (Node) map.get(ConsumerWorkflowEnums.node.name());
		if (null != node) {
			return node;
		}
		return null;
	}

	/**
	 * This method holds logic to set metadata data to generate object lifecycle
	 * events for objectType concept
	 * 
	 * @param node
	 * @param objectMap
	 */
	private void setConceptMetadata(Node node, Map<String, Object> objectMap) {
		if (null != node.getInRelations() && !node.getInRelations().isEmpty()) {
			List<Relation> relations = node.getInRelations();
			for (Relation rel : relations) {
				if (rel.getEndNodeObjectType().equals(ConsumerWorkflowEnums.Concept.name())
						&& rel.getRelationType().equals(ConsumerWorkflowEnums.isParentOf.name())) {
					LOGGER.log("Setting parentid for concept" + rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), rel.getEndNodeId());
				} else if (rel.getEndNodeObjectType().equals(ConsumerWorkflowEnums.Dimension.name())
						&& rel.getRelationType().equals(ConsumerWorkflowEnums.isParentOf.name())) {
					LOGGER.log("Setting parentid for relEndNodeType : Dimension" + rel.getEndNodeObjectType()
							+ rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), rel.getEndNodeObjectType());
				} else {
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
				}

			}
		} else if (null != node.getOutRelations() && !node.getOutRelations().isEmpty()) {
			List<Relation> relations = node.getOutRelations();
			for (Relation rel : relations) {
				if (rel.getEndNodeObjectType().equals(ConsumerWorkflowEnums.Concept.name())
						&& rel.getRelationType().equals(ConsumerWorkflowEnums.isParentOf.name())) {
					LOGGER.log("Setting parentid for concept - outRelations of type concepts"
							+ rel.getEndNodeObjectType() + rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), rel.getEndNodeObjectType());
				} else {
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
				}
			}
		} else {
			objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
			objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
		}
		objectMap.put(ConsumerWorkflowEnums.subtype.name(), "");
	}

	/**
	 * This method holds logic to set metadata data to generate object lifecycle
	 * events for objectType dimensions
	 * 
	 * @param node
	 * @param objectMap
	 */
	private void setDimensionMetadata(Node node, Map<String, Object> objectMap) {
		if (null != node.getInRelations() && !node.getInRelations().isEmpty()) {
			List<Relation> relations = node.getInRelations();
			for (Relation rel : relations) {
				if (rel.getEndNodeObjectType().equals(ConsumerWorkflowEnums.Domain.name())
						&& rel.getRelationType().equals(ConsumerWorkflowEnums.isParentOf.name())) {
					LOGGER.log("Setting parentid for dimension" + rel.getEndNodeObjectType() + rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), rel.getEndNodeObjectType());
				} else {
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
				}
			}
		} else {
			objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
			objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
		}
		objectMap.put(ConsumerWorkflowEnums.subtype.name(), "");
	}

	/**
	 * This method holds logic to set metadata data to generate object lifecycle
	 * events for objectType others
	 * 
	 * @param node
	 * @param objectMap
	 */
	private void setDefaultMetadata(Node node, Map<String, Object> objectMap) {
		objectMap.put(ConsumerWorkflowEnums.subtype.name(), "");
		objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
		objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
	}

	/**
	 * This method holds logic to set metadata data to generate object lifecycle
	 * events for objectType content
	 * 
	 * @param node
	 * @param objectMap
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setContentMetadata(Node node, Map<String, Object> objectMap) {
		if (null != node.getMetadata()) {
			Map<String, Object> nodeMap = new HashMap<String, Object>();
			nodeMap = (Map) node.getMetadata();
			if(null != nodeMap && nodeMap.containsKey("contentType")){
					if (nodeMap.containsValue("Asset")) {
						LOGGER.log("Setting subtype field from mediaType" + nodeMap.get("contentType"));
						objectMap.put(ConsumerWorkflowEnums.objectType.name(),nodeMap.get("contentType"));
						objectMap.put(ConsumerWorkflowEnums.subtype.name(),
								nodeMap.get(ConsumerWorkflowEnums.mediaType.name()));
					} else if (nodeMap.containsValue("Plugin")) {
						LOGGER.log("Checking if node contains category in it"
								+ nodeMap.containsKey(ConsumerWorkflowEnums.category.name()));
						if (nodeMap.containsKey(ConsumerWorkflowEnums.category.name())) {
							String[] category = (String[]) nodeMap.get(ConsumerWorkflowEnums.category.name());
							LOGGER.log("Setting Category as subtype for object_lifecycle_events" + category);
							String subtype = "";
							for (String str : category) {
								subtype = str;
							}
							LOGGER.log("Setting contentType and objectType for as plugin and category" + subtype);
							objectMap.put(ConsumerWorkflowEnums.objectType.name(), ConsumerWorkflowEnums.Plugin.name());
							objectMap.put(ConsumerWorkflowEnums.subtype.name(), subtype);
						} else {
							LOGGER.log("Setting empty subType for plugins without category " + nodeMap.get("contentType"));
							objectMap.put(ConsumerWorkflowEnums.subtype.name(), "");
						}
					} else {
						LOGGER.log("Setting subType field form contentType " + nodeMap.get("contentType"));
						objectMap.put(ConsumerWorkflowEnums.subtype.name(), nodeMap.get("contentType"));
					}
				} else {
					LOGGER.log("Setting subType as empty as contentType is null");
					objectMap.put(ConsumerWorkflowEnums.subtype.name(), "");
				}
		}
		LOGGER.log("Checking if objectType content has inRelations" + node.getInRelations());
		if (null != node.getInRelations() && !node.getInRelations().isEmpty()) {
			List<Relation> relations = node.getInRelations();
			for (Relation rel : relations) {
				if (rel.getEndNodeObjectType().equals("Content") && rel.getRelationType().equals("hasSequenceMember")) {
					LOGGER.log("Setting parentid for Content with inRelations" + rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), rel.getEndNodeObjectType());
				} else {
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
				}
			}
		} else if (null != node.getOutRelations() && !node.getOutRelations().isEmpty()) {
			List<Relation> relations = node.getOutRelations();
			for (Relation rel : relations) {
				if (rel.getEndNodeObjectType().equals(ConsumerWorkflowEnums.Content.name())
						&& rel.getRelationType().equals("hasSequenceMember")) {
					LOGGER.log("Setting parentid for Content with outRelations" + rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), rel.getEndNodeObjectType());
				} else {
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
				}
			}
		} else {
			objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
			objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
		}
	}

	/**
	 * This method holds logic to set metadata data to generate object lifecycle
	 * events for objectType item or assessmentitem
	 * 
	 * @param node
	 * @param objectMap
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setItemMetadata(Node node, Map<String, Object> objectMap) {
		if (null != node.getMetadata()) {
			Map<String, Object> nodeMap = new HashMap<String, Object>();
			nodeMap = (Map) node.getMetadata();
			for (Map.Entry<String, Object> entry : nodeMap.entrySet()) {
				if (entry.getKey().equals(ConsumerWorkflowEnums.type.name())) {
					LOGGER.log("Setting subType field for type from node" + entry.getKey() + entry.getValue());
					objectMap.put(ConsumerWorkflowEnums.subtype.name(), entry.getValue());
				}
			}
		}
		LOGGER.log("Getting relations from AssessmentItem");
		if (null != node.getInRelations() && !node.getInRelations().isEmpty()) {
			List<Relation> relations = node.getInRelations();
			for (Relation rel : relations) {
				if (rel.getEndNodeObjectType().equals(ConsumerWorkflowEnums.ItemSet.name())
						&& rel.getRelationType().equals(ConsumerWorkflowEnums.hasMember.name())) {
					LOGGER.log("Setting parentid for assessmentitem" + rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), rel.getEndNodeId());
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), rel.getEndNodeObjectType());
				} else {
					objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
					objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
				}
			}
		} else {
			objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
			objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
		}
	}

	/**
	 * This Method holds logic to set metadata for ItemSets
	 * 
	 * @param node
	 * @param objectMap
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setItemSetMetadata(Node node, Map<String, Object> objectMap) {
		if (null != node.getMetadata()) {
			Map<String, Object> nodeMap = new HashMap<String, Object>();
			nodeMap = (Map) node.getMetadata();
			for (Map.Entry<String, Object> entry : nodeMap.entrySet()) {
				if (entry.getKey().equals(ConsumerWorkflowEnums.type.name())) {
					LOGGER.log("Setting subType field for type from node" + entry.getKey() + entry.getValue());
					objectMap.put(ConsumerWorkflowEnums.subtype.name(), entry.getValue());
				} else {
					objectMap.put(ConsumerWorkflowEnums.subtype.name(), "");
				}
			}
			objectMap.put(ConsumerWorkflowEnums.parentid.name(), "");
			objectMap.put(ConsumerWorkflowEnums.parenttype.name(), "");
		}
	}
}