package com.hmtmcse.gs

import com.hmtmcse.gs.data.GsApiResponseProperty
import com.hmtmcse.gs.data.GsDomain
import com.hmtmcse.gs.data.GsDomainProperty

trait GsResponseOrganizer<T> {


    abstract LinkedHashMap<String, GsApiResponseProperty> getResponseProperties()
    abstract LinkedHashMap<String, GsApiResponseProperty> setResponseProperties(LinkedHashMap<String, GsApiResponseProperty> responsePropertyLinkedHashMap)
    abstract GsDomain getGsDomain()



    public T includeInResponse(List<String> fields) {
        fields?.each { String field ->
            responseProperties.put(field, new GsApiResponseProperty(field).setDataType(gsDomain.domainProperties.get(field)?.swaggerDataType))
        }
        return this as T
    }

    public T includeInResponse(String fieldName, GsApiResponseProperty gsApiResponseProperty) {
        responseProperties.put(fieldName, gsApiResponseProperty)
        return this as T
    }

    public T excludeFromResponse(List<String> fields) {
        fields?.each { String field ->
            responseProperties.remove(field)
        }
        return this as T
    }


    public T includeAllThenExcludeFromResponse(List<String> fields) {
        includeAllPropertyToResponse()
        excludeFromResponse(fields)
        return this as T
    }

    public T includeAllButExcludeRelationalFromResponse() {
        includeAllPropertyToResponse(false)
        return this as T
    }


    public T includeAllNotRelationalThenExcludeFromResponse(List<String> fields) {
        includeAllPropertyToResponse(false)
        excludeFromResponse(fields)
        return this as T
    }


    public GsApiResponseProperty addResponseProperty(String name, String alias = null, String defaultValue = "") {
        this.responseProperties.put(name, new GsApiResponseProperty(name).setAlias(alias).setDefaultValue(defaultValue).setDataType(gsDomain.domainProperties.get(name)?.swaggerDataType))
        return this.responseProperties.get(name)
    }


    public T includeAllPropertyToResponse(Boolean isRelational = true) {
        responseProperties = domainPropertyToResponseProperty(gsDomain.domainProperties, isRelational)
        return this as T
    }


    LinkedHashMap<String, GsApiResponseProperty> domainPropertyToResponseProperty(LinkedHashMap<String, GsDomainProperty> domainProperties, Boolean isRelational = true) {
        GsApiResponseProperty gsApiResponseProperty
        LinkedHashMap<String, GsApiResponseProperty> responsePropertyLinkedHashMap = new LinkedHashMap<>()
        if (domainProperties == null) {
            return responsePropertyLinkedHashMap
        }
        domainProperties.each { String name, GsDomainProperty gsDomainProperty ->
            gsApiResponseProperty = new GsApiResponseProperty(gsDomainProperty.name).setDataType(gsDomainProperty.swaggerDataType)
            if (gsDomainProperty.isRelationalEntity) {
                if (isRelational) {
                    gsApiResponseProperty.relationalEntity = new GsRelationalEntityResponse()
                    gsApiResponseProperty.relationalEntity.responseProperties = domainPropertyToResponseProperty(gsDomainProperty.relationalProperties)
                } else {
                    return
                }
            }
            responsePropertyLinkedHashMap.put(gsDomainProperty.name, gsApiResponseProperty)
        }
        return responsePropertyLinkedHashMap
    }

}