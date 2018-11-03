package com.hmtmcse.gs

import com.hmtmcse.gs.data.GsApiResponseData
import com.hmtmcse.gs.data.GsApiResponseProperty
import com.hmtmcse.gs.data.GsParamsPairData

class GsRestfulService {

    private def valueFromDomain(String key, def domain, GsApiResponseProperty gsApiResponseProperty){
        try{
            return domain[key]
        }catch(Exception e){
            return gsApiResponseProperty.getDefaultValue()
        }
    }


    def readListProcessor(GsApiActionDefinition definition, Map params) {
        GsInternalResponse responseData = GsInternalResponse.instance()
        GsDataFilterHandler gsDataFilterHandler = GsDataFilterHandler.instance()
        try {
            GsParamsPairData gsParamsPairData = gsDataFilterHandler.getParamsPair(params, definition.domainFields())
            Map pagination = gsDataFilterHandler.readPaginationWithSortProcessor(gsParamsPairData)
            Closure listCriteria = gsDataFilterHandler.readCriteriaProcessor(gsParamsPairData)
            responseData.isSuccess = true
            def queryResult = definition.domain.createCriteria().list(pagination, listCriteria)
            responseData.total = (queryResult ? queryResult.totalCount : 0)
            responseData.response = responseMapGenerator(definition.getResponseProperties(), queryResult, [])
            if (definition.successResponseFormat == null) {
                definition.successResponseFormat = GsApiResponseData.successResponseWithTotal([], 0)
            }
        } catch (Exception e) {
            println(e.getMessage())
            responseData.isSuccess = false
            responseData.message = GsConfigHolder.failedMessage()
        }
        return GsApiResponseData.processAPIResponse(definition, responseData)
    }

    def readDetailsProcessor(GsApiActionDefinition definition, Map params){
        GsInternalResponse responseData = GsInternalResponse.instance()
        try{
            def queryResult = readGetByCondition(definition, params)
            if (queryResult){
                responseData.isSuccess = true
                responseData.response = responseMapGenerator(definition.getResponseProperties(), queryResult)
                if (definition.successResponseFormat == null){
                    definition.successResponseFormat = GsApiResponseData.successResponse([])
                }
            }else{
                responseData.message = GsConfigHolder.failedMessage()
            }
        }catch(GrailsSwaggerException e){
            responseData.isSuccess = false
            responseData.message = e.getMessage()
        }
        return GsApiResponseData.processAPIResponse(definition, responseData)
    }

    def gsReadList(GsApiActionDefinition definition, Map params){
        return readListProcessor(definition, params)
    }


    def responseMapGenerator(Map<String, GsApiResponseProperty> responseProperties, def queryResult, def defaultResponse = [:]) {
        List resultList = []
        Map resultMap = [:]
        if (queryResult) {
            if (queryResult instanceof List) {
                queryResult.each { data ->
                    resultMap = [:]
                    responseProperties.each { String fieldName, GsApiResponseProperty response ->
                        resultMap.put(response.getMapKey(), valueFromDomain(fieldName, data, response))
                    }
                    resultList.add(resultMap)
                }
                return resultList
            } else {
                responseProperties.each { String fieldName, GsApiResponseProperty response ->
                    resultMap.put(response.getMapKey(), valueFromDomain(fieldName, queryResult, response))
                }
                return resultMap
            }
        }
        return defaultResponse
    }


    private GsInternalResponse saveUpdate(Object domain, Map params) {
        GsInternalResponse gsInternalResponse = GsInternalResponse.instance()
        domain.properties = params
        domain.validate()
        if (domain.hasErrors()) {
            return gsInternalResponse.processDomainError(domain.errors.allErrors)
        } else {
            gsInternalResponse.domain = domain.save(flush: true)
        }
        return gsInternalResponse.setIsSuccess(true)
    }


    def gsCreate(GsApiActionDefinition definition, Map params){
        GsDataFilterHandler gsDataFilterHandler = GsDataFilterHandler.instance()
        GsInternalResponse gsInternalResponse = gsDataFilterHandler.saveUpdateDataFilter(definition, params)
        if (gsInternalResponse.isSuccess){
            gsInternalResponse = saveUpdate(definition.domain.newInstance(), gsInternalResponse.filteredParams)
        }
        if (definition.successResponseFormat == null){
            definition.successResponseFormat = GsConfigHolder.defaultSuccessResponse
        }
        return GsApiResponseData.processAPIResponse(definition, gsInternalResponse)
    }

    def gsDetails(GsApiActionDefinition definition, Map params){
        return readDetailsProcessor(definition, params)
    }


    def readGetByCondition(GsApiActionDefinition definition, Map params) throws GrailsSwaggerException{
        def queryResult = null
        GsDataFilterHandler gsDataFilterHandler = GsDataFilterHandler.instance()
        try{
            GsParamsPairData gsParamsPairData = gsDataFilterHandler.getParamsPair(params, definition.domainFields())
            Closure listCriteria = gsDataFilterHandler.readCriteriaProcessor(gsParamsPairData, false, "details")
            queryResult = definition.domain.createCriteria().get(listCriteria)
        }catch(Exception e){
            String message = GsExceptionParser.exceptionMessage(e)
            throw new GrailsSwaggerException(message)
        }
        return queryResult
    }



    def gsUpdate(GsApiActionDefinition definition, Map params){

    }

    def gsDelete(GsApiActionDefinition definition, Map params){}


    def gsBulkUpdate(GsApiActionDefinition definition, Map params){}

    def gsBulkDelete(GsApiActionDefinition definition, Map params){}

    def gsCustomQuery(GsApiActionDefinition definition, Map params){}

    def gsCustomQueryAndResponse(GsApiActionDefinition definition, Map params){}




}
