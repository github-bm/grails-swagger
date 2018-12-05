package com.hmtmcse.gs

import com.hmtmcse.gs.data.GsAction
import com.hmtmcse.gs.data.GsApiRequestProperty
import com.hmtmcse.gs.data.GsApiResponseData
import com.hmtmcse.gs.data.GsApiResponseProperty
import com.hmtmcse.gs.data.GsControllerActionData
import com.hmtmcse.gs.data.GsRequestResponseProperty
import com.hmtmcse.swagger.definition.*

import java.lang.reflect.InvocationTargetException

class SwaggerUIGeneratorService {

    private SwaggerDefinition swaggerDefinition = new SwaggerDefinition()
    private String failedResponseName = "FailedResponse"

    def generate() {
        try{
            startSwagger(GsConfigHolder.hostnameWithPort, GsUrlMappingUtil.apiPrefix())
            GsUrlMappingUtil.getUrlMappingData().each { GsControllerActionData controllerActionData ->
                swaggerJsonByControllerData(controllerActionData)
                addDefaultFailedResponse()
            }
        }catch(Exception e){
           println("Generate: " + e.getMessage())
        }
        return swaggerDefinition.getDefinition()
    }

    void addDefaultFailedResponse(){
        SwaggerProperty swaggerProperty = GsApiResponseData.swaggerResponseProperty(GsConfigHolder.defaultFailedResponse)
        if (swaggerProperty){
            addToDefinition(failedResponseName, SwaggerConstant.SWAGGER_DT_OBJECT, swaggerProperty)
        }else{
            failedResponseName = null
        }
    }


    void swaggerJsonByControllerData(GsControllerActionData controllerActionData) {
        try{
            String tagName = GsUtil.makeHumReadble(controllerActionData.controllerUrlName)
            String description = ""
            def controllerObj = GsReflectionUtil.getNewObject(controllerActionData.controllerClass)
            if (controllerObj) {
                try{
                    controllerObj?.swaggerInit()
                    tagName = controllerObj?.tagName ?: tagName
                    description = controllerObj?.tagDescription ?: ""
                }catch(Exception e){}
            }
            processApiActionDefinition(controllerActionData, tagName)
            swaggerDefinition.addTag(tagName, description)
        }catch(Exception e){
            println("Invalid Controller Or Action: " + e.getMessage())
        }
    }


    void processApiActionDefinition(GsControllerActionData controllerActionData, String tagName) {
        def controllerObj = GsReflectionUtil.getNewObjectWithServiceInstance(controllerActionData.controllerClass)
        GsApiActionDefinition gsApiActionDefinition = null
        String url = "/${controllerActionData.apiVersion}/${controllerActionData.url}/"
        String actionUrl
        if (controllerObj) {
            controllerObj.returnFor = GsConstant.RETURN_FOR_DEFINITION
            SwaggerPath swaggerPath
            SwaggerProperty swaggerProperty
            SwaggerPathResponse response
            controllerActionData.actions.each { GsAction gsAction ->
                actionUrl = null
                try {
                    gsApiActionDefinition = controllerObj."$gsAction.actionRealName"()
                    if (gsApiActionDefinition) {
                        gsApiActionDefinition.setModelDefinition(controllerActionData.apiVersion, controllerActionData.controllerUrlName, gsAction)
                        swaggerPath = pathGenerator(gsAction, gsApiActionDefinition)
                        swaggerPath.addTag(tagName)
                        actionUrl = "${url}${gsAction.name}"
                        swaggerDefinition.startPaths(actionUrl).addPath(gsAction.httpMethod, swaggerPath)
                        response = responseGenerator(gsAction, gsApiActionDefinition)
                        swaggerPath.addResponse(response)
                    }
                } catch (InvocationTargetException e) {
                    println("controller:${controllerActionData.controllerName} actionName:${gsAction.actionRealName} processApiActionDefinition: " + e.getMessage())
                } catch (NullPointerException e) {
                    println("controller:${controllerActionData.controllerName} actionName:${gsAction.actionRealName} processApiActionDefinition: " + e.getMessage())
                } catch (Exception e) {
                    println("controller:${controllerActionData.controllerName} actionName:${gsAction.actionRealName} processApiActionDefinition: " + e.getMessage())
                }
            }
        }
    }




    SwaggerPathResponse responseGenerator(GsAction gsAction, GsApiActionDefinition gsApiActionDefinition){
        SwaggerPathResponse response = new SwaggerPathResponse()
        if (gsApiActionDefinition.successResponseFormat == null){
            gsApiActionDefinition.successResponseFormat = GsConfigHolder.defaultSuccessResponse
        }
        String successResponseDefinition = "${SwaggerConstant.SUCCESS_RESPONSE}${gsApiActionDefinition.modelDefinition}"
        if (gsApiActionDefinition.successResponseFormat.response != null){
            SwaggerProperty swaggerProperty = responsePropertiesProcessor(gsApiActionDefinition.getResponseProperties(), null, gsApiActionDefinition.domainFields())
            SwaggerProperty successResponse = GsApiResponseData.swaggerResponseProperty(gsApiActionDefinition.successResponseFormat)

            if (gsApiActionDefinition.successResponseFormat.response instanceof  List){
                successResponse.arrayProperty(GsConfigHolder.responseKey(), swaggerProperty)
            }else{
                successResponse.objectProperty(GsConfigHolder.responseKey(), swaggerProperty)
            }
            addToDefinition(successResponseDefinition, SwaggerConstant.SWAGGER_DT_OBJECT, successResponse)
            response.start(SwaggerConstant.SUCCESS_RESPONSE)
            response.description(GsUtil.makeHumReadble(SwaggerConstant.SUCCESS_RESPONSE)).schemaOnly(successResponseDefinition)
        }else{
            SwaggerProperty successResponse = GsApiResponseData.swaggerResponseProperty(gsApiActionDefinition.successResponseFormat)
            addToDefinition(successResponseDefinition, SwaggerConstant.SWAGGER_DT_OBJECT, successResponse)
            response.start(SwaggerConstant.SUCCESS_RESPONSE)
            response.description(GsUtil.makeHumReadble(SwaggerConstant.SUCCESS_RESPONSE)).schemaOnly(successResponseDefinition)
        }


        String failedResponseDefinition = "${SwaggerConstant.FAILED_RESPONSE}${gsApiActionDefinition.modelDefinition}"
        if (failedResponseName && gsApiActionDefinition.failedResponseFormat == null){
            response.start(SwaggerConstant.DEFAULT_FAILED_RESPONSE)
            response.description(GsUtil.makeHumReadble(SwaggerConstant.DEFAULT_FAILED_RESPONSE)).schemaOnly(failedResponseName)
        }

        return response
    }


    SwaggerPath pathGenerator(GsAction gsAction, GsApiActionDefinition gsApiActionDefinition) {

        SwaggerPath swaggerPath = swaggerDefinition.path()
        String message = "Please Use Http GET Request for "
        SwaggerPathParameter parameter = null
        SwaggerProperty swaggerProperty = null
        String requestDefinition = "${SwaggerConstant.REQUEST}${gsApiActionDefinition.modelDefinition}"
        GsFilterResolver gsFilterResolver = new GsFilterResolver()

        if (gsAction.httpMethod && gsAction.httpMethod.equals(GsConstant.GET)) {
            swaggerProperty = requestPropertiesProcessor(gsApiActionDefinition.getRequestProperties(), SwaggerConstant.IN_QUERY)
            swaggerProperty = gsFilterResolver.resolveSwaggerDefinition(gsApiActionDefinition, SwaggerConstant.IN_QUERY, swaggerProperty)
            swaggerPath.parameters(swaggerProperty.getPropertyList())

        } else if (gsAction.httpMethod && gsAction.httpMethod.equals(GsConstant.POST)) {
            message = "Please Use Http POST Request for "
            parameter = swaggerDefinition.pathParameter(SwaggerConstant.IN_BODY, gsApiActionDefinition.parameterName ?: SwaggerConstant.IN_BODY)
            parameter.required().schema(requestDefinition)
            swaggerProperty = requestPropertiesProcessor(gsApiActionDefinition.getRequestProperties(), SwaggerConstant.IN_BODY)
            if (gsApiActionDefinition.enablePaginationAndSorting){
                swaggerProperty = gsFilterResolver.swaggerPagination(swaggerProperty, SwaggerConstant.IN_BODY)
            }
            swaggerProperty = gsFilterResolver.swaggerWhere(gsApiActionDefinition,  swaggerProperty, SwaggerConstant.IN_BODY)
            addToDefinition(requestDefinition, SwaggerConstant.SWAGGER_DT_OBJECT, swaggerProperty)

            swaggerPath.addConsumeType(SwaggerConstant.APPLICATION_JSON)
            if (parameter){
                swaggerPath.addParameter(parameter)
            }

        } else if (gsAction.httpMethod && gsAction.httpMethod.equals(GsConstant.DELETE)) {
            message = "Please Use Http DELETE Request for "
            parameter = swaggerDefinition.pathParameter(SwaggerConstant.IN_BODY, gsApiActionDefinition.parameterName ?: SwaggerConstant.IN_BODY)
            parameter.required().schema(requestDefinition)
            swaggerProperty = requestPropertiesProcessor(gsApiActionDefinition.getRequestProperties(), SwaggerConstant.IN_BODY)
            if (gsApiActionDefinition.enablePaginationAndSorting){
                swaggerProperty = gsFilterResolver.swaggerPagination(swaggerProperty, SwaggerConstant.IN_BODY)
            }
            swaggerProperty = gsFilterResolver.swaggerWhere(gsApiActionDefinition,  swaggerProperty, SwaggerConstant.IN_BODY)
            addToDefinition(requestDefinition, SwaggerConstant.SWAGGER_DT_OBJECT, swaggerProperty)

            swaggerPath.addConsumeType(SwaggerConstant.APPLICATION_JSON)
            swaggerPath.addParameter(parameter)


        } else if (gsAction.httpMethod && gsAction.httpMethod.equals(GsConstant.PUT)) {
            message = "Please Use Http PUT Request for "
        } else {
            message = "Please Use Http Request for "
        }

        if (!gsApiActionDefinition.summary) {
            gsApiActionDefinition.summary = GsUtil.makeHumReadble(message + gsAction.name)
        }
        swaggerPath.summary(gsApiActionDefinition.summary)

        if (gsApiActionDefinition.description) {
            swaggerPath.description(gsApiActionDefinition.description)
        }

        return swaggerPath

    }

    SwaggerProperty requestPropertiesProcessor(LinkedHashMap<String, GsApiRequestProperty> requestPropertyMap, String inType) {
        SwaggerProperty swaggerProperty = new SwaggerProperty()
        requestPropertyMap.each { String name, GsApiRequestProperty field ->

            if (field.dataType == null || field.dataType.equals("")) {
                field.dataType = SwaggerConstant.SWAGGER_DT_STRING
            }

            swaggerProperty.property(field.name, field.dataType)
            if (field.format) {
                swaggerProperty.format(field.format)
            }else{
                switch (field.dataType){
                    case SwaggerConstant.SWAGGER_DT_LONG:
                        field.dataType = SwaggerConstant.SWAGGER_DT_INTEGER
                        field.format = SwaggerConstant.SWAGGER_FM_INT64
                        break
                    case SwaggerConstant.SWAGGER_DT_INTEGER:
                        field.dataType = SwaggerConstant.SWAGGER_DT_INTEGER
                        field.format = SwaggerConstant.SWAGGER_FM_INT32
                        break
                    case SwaggerConstant.SWAGGER_DT_FLOAT:
                        field.dataType = SwaggerConstant.SWAGGER_DT_NUMBER
                        field.format = SwaggerConstant.SWAGGER_FM_FLOAT
                        break
                    case SwaggerConstant.SWAGGER_DT_DOUBLE:
                        field.dataType = SwaggerConstant.SWAGGER_DT_NUMBER
                        field.format = SwaggerConstant.SWAGGER_FM_DOUBLE
                        break
                    case SwaggerConstant.SWAGGER_DT_STRING_DATE:
                        field.dataType = SwaggerConstant.SWAGGER_DT_STRING
                        field.format = SwaggerConstant.SWAGGER_FM_DATE
                        break
                }
            }

            if (field.relationalEntity) {
                swaggerProperty.objectProperty(name, requestPropertiesProcessor(field.relationalEntity.requestProperties, inType))
            }

            if (field.example) {
                swaggerProperty.example(field.example)
            }

            if (field.description) {
                swaggerProperty.description(field.description)
            }

            if (inType) {
                swaggerProperty.in(inType)
            }

            if (inType && inType.equals(SwaggerConstant.IN_QUERY) && field.isRequired){
                swaggerProperty.required()
            }

            swaggerProperty.addToList()
        }
        return swaggerProperty
    }



    SwaggerProperty propertiesProcessor(Map<String, GsRequestResponseProperty> responsePropertyMap, String inType, Map domainFields) {
        SwaggerProperty swaggerProperty = new SwaggerProperty()
        responsePropertyMap.each { String name, GsRequestResponseProperty field ->
            if (field.referenceDefinition) {

            } else {
                if (field.dataType == null || field.dataType.equals("")) {
                    field.dataType = SwaggerConstant.SWAGGER_DT_STRING
                    if (domainFields.get(field.name)) {
                        field.dataType = domainFields.get(field.name)
                    }
                }
                swaggerProperty.property(field.name, field.dataType)
                if (field.format) {
                    swaggerProperty.format(field.format)
                }
            }


            if (field.example) {
                swaggerProperty.example(field.example)
            }

            if (field.description) {
                swaggerProperty.description(field.description)
            }

            if (inType) {
                swaggerProperty.in(inType)
            }
            swaggerProperty.addToList()
        }
        return swaggerProperty
    }



    SwaggerProperty responsePropertiesProcessor(LinkedHashMap<String, GsApiResponseProperty> responsePropertyMap, String inType, Map domainFields) {
        SwaggerProperty swaggerProperty = new SwaggerProperty()
        responsePropertyMap.each { String name, GsApiResponseProperty field ->
            if (field.referenceDefinition) {

            } else {
                if (field.dataType == null || field.dataType.equals("")) {
                    field.dataType = SwaggerConstant.SWAGGER_DT_STRING
                    if (domainFields.get(field.name)) {
                        field.dataType = domainFields.get(field.name)
                    }
                }
                swaggerProperty.property(field.name, field.dataType)
                if (field.format) {
                    swaggerProperty.format(field.format)
                }
            }

            if (field.relationalEntity) {
                swaggerProperty.objectProperty(name, responsePropertiesProcessor(field.relationalEntity.responseProperties, inType, domainFields[name]))
            }

            if (field.example) {
                swaggerProperty.example(field.example)
            }

            if (field.description) {
                swaggerProperty.description(field.description)
            }

            if (inType) {
                swaggerProperty.in(inType)
            }
            swaggerProperty.addToList()
        }
        return swaggerProperty
    }


    SwaggerProperty requestPropertiesProcessor(LinkedHashMap<String, GsApiRequestProperty> requestPropertyMap, String inType, Map domainFields) {
        SwaggerProperty swaggerProperty = new SwaggerProperty()
        requestPropertyMap.each { String name, GsApiRequestProperty field ->
            if (field.referenceDefinition) {

            } else {
                if (field.dataType == null || field.dataType.equals("")) {
                    field.dataType = SwaggerConstant.SWAGGER_DT_STRING
                    if (domainFields.get(field.name)) {
                        field.dataType = domainFields.get(field.name)
                    }
                }
                swaggerProperty.property(field.name, field.dataType)
                if (field.format) {
                    swaggerProperty.format(field.format)
                }
            }

            if (field.relationalEntity) {
                swaggerProperty.objectProperty(name, requestPropertiesProcessor(field.relationalEntity.requestProperties, inType, domainFields[name]))
            }

            if (field.example) {
                swaggerProperty.example(field.example)
            }

            if (field.description) {
                swaggerProperty.description(field.description)
            }

            if (inType) {
                swaggerProperty.in(inType)
            }
            swaggerProperty.addToList()
        }
        return swaggerProperty
    }





    void startSwagger(String host, String basePath){
        swaggerDefinition = new SwaggerDefinition()
        swaggerDefinition.host(host)
        swaggerDefinition.basePath("/" + basePath)
        swaggerDefinition.scheme("http")
    }



    void addToDefinition(String name, String type, SwaggerProperty swaggerProperty){
        swaggerDefinition.addDefinition(name, type).addProperties(swaggerProperty)
    }
}
