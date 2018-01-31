package org.ekstep.dac.impl.entity.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.ekstep.common.Platform;
import org.ekstep.dac.enums.AuditHistoryConstants;
import org.ekstep.searchindex.dto.SearchDTO;
import org.ekstep.searchindex.elasticsearch.ElasticSearchUtil;
import org.ekstep.searchindex.processor.SearchProcessor;
import org.ekstep.telemetry.logger.TelemetryManager;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component("auditHistoryEsDao")
public class AuditHistoryEsDao {

	/** The Logger */
	
	
	/** The Object Mapper */
	private ObjectMapper mapper = new ObjectMapper();

	/** The ElasticSearchUtil */
	private ElasticSearchUtil es = null;

	/** The SearchProcessor */
	private SearchProcessor processor = null;

	private String host = "http://localhost";
	private int port = 9200;

	@PostConstruct
	public void init() {
		host = (Platform.config.hasPath("audit.es_host")) ? Platform.config.getString("audit.es_host") : host;
		port = (Platform.config.hasPath("audit.es_port")) ? Platform.config.getInt("audit.es_port") : port;
		es = new ElasticSearchUtil(host, port);
		processor = new SearchProcessor(host, port);
	}
	
	public void save(Map<String, Object> entity_map) throws IOException {
			createIndex();
			addDocument(entity_map);
	}
	
	public List<Object> search(SearchDTO search) {
		List<Object>  result= new ArrayList<Object>();
			try {
				TelemetryManager.log("sending search request to search processor" + search);
				result = (List<Object>) processor.processSearchQuery(search, false, AuditHistoryConstants.AUDIT_HISTORY_INDEX);
				TelemetryManager.log("result from search processor: " + result);
			} catch (Exception e) {
				TelemetryManager.error("error while processing the search request: "+ e.getMessage(), e);
				e.printStackTrace();
			}
		return result;
	}
	
	public void createIndex() throws IOException {
		String settings = "{ \"settings\": {   \"index\": {     \"index\": \""+AuditHistoryConstants.AUDIT_HISTORY_INDEX+"\",     \"type\": \""+AuditHistoryConstants.AUDIT_HISTORY_INDEX_TYPE+"\",     \"analysis\": {       \"analyzer\": {         \"ah_index_analyzer\": {           \"type\": \"custom\",           \"tokenizer\": \"standard\",           \"filter\": [             \"lowercase\",             \"mynGram\"           ]         },         \"ah_search_analyzer\": {           \"type\": \"custom\",           \"tokenizer\": \"standard\",           \"filter\": [             \"standard\",             \"lowercase\"           ]         },         \"keylower\": {           \"tokenizer\": \"keyword\",           \"filter\": \"lowercase\"         }       },       \"filter\": {         \"mynGram\": {           \"type\": \"nGram\",           \"min_gram\": 1,           \"max_gram\": 20,           \"token_chars\": [             \"letter\",             \"digit\",             \"whitespace\",             \"punctuation\",             \"symbol\"           ]         }       }     }   } }}";
		String mappings = "{ \""+AuditHistoryConstants.AUDIT_HISTORY_INDEX_TYPE+"\" : {    \"dynamic_templates\": [      {        \"longs\": {          \"match_mapping_type\": \"long\",          \"mapping\": {            \"type\": \"long\",            fields: {              \"raw\": {                \"type\": \"long\"              }            }          }        }      },      {        \"booleans\": {          \"match_mapping_type\": \"boolean\",          \"mapping\": {            \"type\": \"boolean\",            fields: {              \"raw\": {                \"type\": \"boolean\"              }            }          }        }      },{        \"doubles\": {          \"match_mapping_type\": \"double\",          \"mapping\": {            \"type\": \"double\",            fields: {              \"raw\": {                \"type\": \"double\"              }            }          }        }      },	  {        \"dates\": {          \"match_mapping_type\": \"date\",          \"mapping\": {            \"type\": \"date\",            fields: {              \"raw\": {                \"type\": \"date\"              }            }          }        }      },      {        \"strings\": {          \"match_mapping_type\": \"string\",          \"mapping\": {            \"type\": \"string\",            \"copy_to\": \"all_fields\",            \"analyzer\": \"ah_index_analyzer\",            \"search_analyzer\": \"ah_search_analyzer\",            fields: {              \"raw\": {                \"type\": \"string\",                \"analyzer\": \"keylower\"              }            }          }        }      }    ],    \"properties\": {      \"all_fields\": {        \"type\": \"string\",        \"analyzer\": \"ah_index_analyzer\",        \"search_analyzer\": \"ah_search_analyzer\",        fields: {          \"raw\": {            \"type\": \"string\",            \"analyzer\": \"keylower\"          }        }      }    }  }}";
		TelemetryManager.log("Creating Audit History Index : " + AuditHistoryConstants.AUDIT_HISTORY_INDEX);
		es.addIndex(AuditHistoryConstants.AUDIT_HISTORY_INDEX, AuditHistoryConstants.AUDIT_HISTORY_INDEX_TYPE,
				settings, mappings);
	}

	public void addDocument(Map<String, Object> request) throws IOException {
		String document = null;
		TelemetryManager.log("Checking if document is empty : " + request);
		if(!request.isEmpty()){
			document = mapper.writeValueAsString(request);
			TelemetryManager.log("converting request map tp string : " + document);
		}
		if(StringUtils.isNotBlank(document)){
			es.addDocument(AuditHistoryConstants.AUDIT_HISTORY_INDEX, AuditHistoryConstants.AUDIT_HISTORY_INDEX_TYPE, document);
			TelemetryManager.log("Adding document to Audit History Index : " + document);
		}
	}

	public void delete(String query) throws IOException {
		TelemetryManager.log("deleting Audit History Index : " + AuditHistoryConstants.AUDIT_HISTORY_INDEX);
		es.deleteDocumentsByQuery(query.toString(), AuditHistoryConstants.AUDIT_HISTORY_INDEX, AuditHistoryConstants.AUDIT_HISTORY_INDEX_TYPE);
		TelemetryManager.log("Documents deleted from Audit History Index");
	}
	
	@PreDestroy
	public void shutdown(){
		TelemetryManager.info("shuting down elastic search instance.");
		es.finalize();
	}
}
