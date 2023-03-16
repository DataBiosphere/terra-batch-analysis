package bio.terra.cbas.util.methods;

import bio.terra.cbas.common.exceptions.WomtoolValueTypeProcessingException.WomtoolValueTypeNotFoundException;
import bio.terra.cbas.model.OutputDestination;
import bio.terra.cbas.model.OutputDestinationNone;
import bio.terra.cbas.model.ParameterDefinition;
import bio.terra.cbas.model.ParameterDefinitionLiteralValue;
import bio.terra.cbas.model.ParameterTypeDefinition;
import bio.terra.cbas.model.ParameterTypeDefinitionArray;
import bio.terra.cbas.model.ParameterTypeDefinitionMap;
import bio.terra.cbas.model.ParameterTypeDefinitionOptional;
import bio.terra.cbas.model.ParameterTypeDefinitionPrimitive;
import bio.terra.cbas.model.ParameterTypeDefinitionStruct;
import bio.terra.cbas.model.PrimitiveParameterValueType;
import bio.terra.cbas.model.StructField;
import bio.terra.cbas.model.WorkflowInputDefinition;
import bio.terra.cbas.model.WorkflowOutputDefinition;
import cromwell.client.model.ToolInputParameter;
import cromwell.client.model.ToolOutputParameter;
import cromwell.client.model.ValueType;
import cromwell.client.model.ValueTypeObjectFieldTypesInner;
import cromwell.client.model.WorkflowDescription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WomtoolToCbasInputsAndOutputs {

  private WomtoolToCbasInputsAndOutputs() {
    throw new UnsupportedOperationException("Cannot be instantiated");
  }

  public static ParameterTypeDefinition getParameterType(ValueType input, String typeDisplayName)
      throws WomtoolValueTypeNotFoundException {

    List<StructField> fields = new ArrayList<>();

    return switch (Objects.requireNonNull(input.getValueType().getTypeName())) {
      case STRING -> new ParameterTypeDefinitionPrimitive()
          .primitiveType(PrimitiveParameterValueType.STRING)
          .type(ParameterTypeDefinition.TypeEnum.PRIMITIVE);
      case INT -> new ParameterTypeDefinitionPrimitive()
          .primitiveType(PrimitiveParameterValueType.INT)
          .type(ParameterTypeDefinition.TypeEnum.PRIMITIVE);
      case BOOLEAN -> new ParameterTypeDefinitionPrimitive()
          .primitiveType(PrimitiveParameterValueType.BOOLEAN)
          .type(ParameterTypeDefinition.TypeEnum.PRIMITIVE);
      case FILE -> new ParameterTypeDefinitionPrimitive()
          .primitiveType(PrimitiveParameterValueType.FILE)
          .type(ParameterTypeDefinition.TypeEnum.PRIMITIVE);
      case FLOAT -> new ParameterTypeDefinitionPrimitive()
          .primitiveType(PrimitiveParameterValueType.FLOAT)
          .type(ParameterTypeDefinition.TypeEnum.PRIMITIVE);
      case OPTIONAL -> new ParameterTypeDefinitionOptional()
          .optionalType(
              getParameterType(Objects.requireNonNull(input))
                  .type(ParameterTypeDefinition.TypeEnum.OPTIONAL))
          .type(ParameterTypeDefinition.TypeEnum.OPTIONAL);
      case ARRAY -> new ParameterTypeDefinitionArray()
          .nonEmpty(input.getValueType().getNonEmpty())
          .arrayType(getParameterType(Objects.requireNonNull(input)))
          .type(ParameterTypeDefinition.TypeEnum.ARRAY);
      case MAP -> new ParameterTypeDefinitionMap()
          .keyType(
              PrimitiveParameterValueType.fromValue(
                  Objects.requireNonNull(
                          Objects.requireNonNull(input.get.getMapType()).getKeyType().getTypeName())
                      .toString()))
          .valueType(
              getParameterType(Objects.requireNonNull(valueType.getMapType()).getValueType()))
          .type(ParameterTypeDefinition.TypeEnum.MAP);
      case OBJECT -> {
        for (ValueTypeObjectFieldTypesInner innerField :
            Objects.requireNonNull(input.getValueType().getObjectFieldTypes())) {
          StructField structField = new StructField();
          structField.fieldName(innerField.getFieldName());
          structField.fieldType(
              getParameterType(input));
          fields.add(structField);
        }
        yield new ParameterTypeDefinitionStruct()
            .name(typeDisplayName)
            .fields(fields)
            .type(ParameterTypeDefinition.TypeEnum.STRUCT);
      }
      default -> throw new WomtoolValueTypeNotFoundException(input.getValueType());
    };
  }

  public static List<WorkflowInputDefinition> womToCbasInputBuilder(WorkflowDescription womInputs)
      throws WomtoolValueTypeNotFoundException {
    List<WorkflowInputDefinition> cbasInputDefinition = new ArrayList<>();
    String workflowName = womInputs.getName();
    // List<StructField> fields = new ArrayList<>();

    for (ToolInputParameter input : womInputs.getInputs()) {
      WorkflowInputDefinition workflowInputDefinition = new WorkflowInputDefinition();

      // Name
      workflowInputDefinition.inputName("%s.%s".formatted(workflowName, input.getName()));

      // Input type
        workflowInputDefinition.inputType(getParameterType(input));

      // Source
      workflowInputDefinition.source(
          new ParameterDefinitionLiteralValue()
              .parameterValue(input.getDefault())
              .type(ParameterDefinition.TypeEnum.NONE));

      cbasInputDefinition.add(workflowInputDefinition);
    }

    return cbasInputDefinition;
  }

  // Outputs
  public static List<WorkflowOutputDefinition> womToCbasOutputBuilder(
      WorkflowDescription womOutputs) throws WomtoolValueTypeNotFoundException {
    List<WorkflowOutputDefinition> cbasOutputs = new ArrayList<>();

    for (ToolOutputParameter output : womOutputs.getOutputs()) {
      WorkflowOutputDefinition workflowOutputDefinition = new WorkflowOutputDefinition();

      // Name
      String workflowName = womOutputs.getName();
      workflowOutputDefinition.outputName("%s.%s".formatted(workflowName, output.getName()));

      // ValueType
      workflowOutputDefinition.outputType(getParameterType(output.getValueType(), null, output));

      // Destination
      workflowOutputDefinition.destination(
          new OutputDestinationNone().type(OutputDestination.TypeEnum.NONE));

      cbasOutputs.add(workflowOutputDefinition);
    }

    return cbasOutputs;
  }
}
