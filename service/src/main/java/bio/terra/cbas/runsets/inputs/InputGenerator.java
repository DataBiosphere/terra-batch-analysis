package bio.terra.cbas.runsets.inputs;

import bio.terra.cbas.model.ParameterDefinition;
import bio.terra.cbas.model.ParameterDefinitionEntityLookup;
import bio.terra.cbas.model.ParameterDefinitionLiteralValue;
import bio.terra.cbas.model.WorkflowParamDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.databiosphere.workspacedata.model.EntityResponse;

public class InputGenerator {

  private static final JsonMapper jsonMapper =
      // Json order doesn't really matter, but for test cases it's convenient to have them
      // be consisten.
      JsonMapper.builder()
          .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
          .build();

  public static Map<String, Object> buildInputs(
      List<WorkflowParamDefinition> inputDefinitions, EntityResponse entity) {
    Map<String, Object> params = new HashMap<>();
    for (WorkflowParamDefinition param : inputDefinitions) {
      String parameterName = param.getParameterName();
      Object parameterValue;
      if (param.getSource().getType() == ParameterDefinition.TypeEnum.LITERAL) {
        parameterValue = ((ParameterDefinitionLiteralValue) param.getSource()).getParameterValue();
      } else {
        String attributeName =
            ((ParameterDefinitionEntityLookup) param.getSource()).getEntityAttribute();
        parameterValue = entity.getAttributes().get(attributeName);
      }
      params.put(parameterName, parameterValue);
    }
    return params;
  }

  public static String inputsToJson(Map<String, Object> inputs) throws JsonProcessingException {
    return jsonMapper.writeValueAsString(inputs);
  }

  private InputGenerator() {
    // Do not use. No construction necessary for static utility class.
  }
}
