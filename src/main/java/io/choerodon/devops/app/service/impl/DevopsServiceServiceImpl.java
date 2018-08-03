package io.choerodon.devops.app.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.models.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.choerodon.core.convertor.ConvertHelper;
import io.choerodon.core.convertor.ConvertPageHelper;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.dto.DevopsServiceDTO;
import io.choerodon.devops.api.dto.DevopsServiceReqDTO;
import io.choerodon.devops.api.validator.DevopsServiceValidator;
import io.choerodon.devops.app.service.DevopsIngressService;
import io.choerodon.devops.app.service.DevopsServiceService;
import io.choerodon.devops.domain.application.entity.*;
import io.choerodon.devops.domain.application.factory.DevopsEnvCommandFactory;
import io.choerodon.devops.domain.application.repository.*;
import io.choerodon.devops.domain.application.valueobject.DevopsServiceV;
import io.choerodon.devops.domain.service.IDevopsIngressService;
import io.choerodon.devops.domain.service.IDevopsServiceService;
import io.choerodon.devops.infra.common.util.EnvUtil;
import io.choerodon.devops.infra.common.util.enums.CommandStatus;
import io.choerodon.devops.infra.common.util.enums.CommandType;
import io.choerodon.devops.infra.common.util.enums.ObjectType;
import io.choerodon.devops.infra.common.util.enums.ServiceStatus;
import io.choerodon.devops.infra.dataobject.DevopsIngressDO;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.websocket.helper.EnvListener;

/**
 * Created by Zenger on 2018/4/13.
 */
@Service
@Transactional(rollbackFor = RuntimeException.class)
public class DevopsServiceServiceImpl implements DevopsServiceService {

    private Gson gson = new Gson();

    @Autowired
    private DevopsServiceRepository devopsServiceRepository;
    @Autowired
    private DevopsEnvironmentRepository devopsEnviromentRepository;
    @Autowired
    private ApplicationInstanceRepository applicationInstanceRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private IDevopsServiceService idevopsServiceService;
    @Autowired
    private DevopsEnvCommandRepository devopsEnvCommandRepository;
    @Autowired
    private DevopsServiceInstanceRepository devopsServiceInstanceRepository;
    @Autowired
    private DevopsIngressRepository devopsIngressRepository;
    @Autowired
    private DevopsIngressService devopsIngressService;
    @Autowired
    private IDevopsIngressService idevopsIngressService;
    @Autowired
    private EnvListener envListener;
    @Autowired
    private EnvUtil envUtil;

    @Override
    public Boolean checkName(Long projectId, Long envId, String name) {
        return devopsServiceRepository.checkName(projectId, envId, name);
    }

    @Override
    public Page<DevopsServiceDTO> listDevopsServiceByPage(Long projectId, PageRequest pageRequest, String searchParam) {
        return listByEnv(projectId, null, pageRequest, searchParam);
    }

    @Override
    public Page<DevopsServiceDTO> listByEnv(Long projectId, Long envId, PageRequest pageRequest, String searchParam) {
        Page<DevopsServiceV> devopsServiceByPage = devopsServiceRepository.listDevopsServiceByPage(
                projectId, envId, pageRequest, searchParam);
        List<Long> connectedEnvList = envUtil.getConnectedEnvList(envListener);
        List<Long> updatedEnvList = envUtil.getUpdatedEnvList(envListener);
        devopsServiceByPage.parallelStream().forEach(devopsServiceV -> {
            if (connectedEnvList.contains(devopsServiceV.getEnvId())
                    && updatedEnvList.contains(devopsServiceV.getEnvId())) {
                devopsServiceV.setEnvStatus(true);
            }
        });
        return ConvertPageHelper.convertPage(devopsServiceByPage, DevopsServiceDTO.class);
    }


    @Override
    public List<DevopsServiceDTO> listDevopsService(Long envId) {
        return ConvertHelper.convertList(
                devopsServiceRepository.listDevopsService(envId), DevopsServiceDTO.class);
    }

    @Override
    public DevopsServiceDTO query(Long id) {
        return ConvertHelper.convert(devopsServiceRepository.selectById(id), DevopsServiceDTO.class);
    }


    @Override
    public Boolean insertDevopsService(Long projectId, DevopsServiceReqDTO devopsServiceReqDTO) {
        envUtil.checkEnvConnection(devopsServiceReqDTO.getEnvId(), envListener);
        DevopsServiceValidator.checkService(devopsServiceReqDTO);
        DevopsEnvironmentE devopsEnvironmentE =
                devopsEnviromentRepository.queryById(devopsServiceReqDTO.getEnvId());
        if (devopsEnvironmentE == null) {
            throw new CommonException("error.env.query");
        }

        if (!devopsServiceRepository.checkName(projectId, devopsEnvironmentE.getId(), devopsServiceReqDTO.getName())) {
            throw new CommonException("error.service.name.exist");
        }
        checkOptions(devopsServiceReqDTO.getEnvId(), devopsServiceReqDTO.getAppId(),
                null, null);

        ApplicationE applicationE = getApplicationE(devopsServiceReqDTO.getAppId());
        DevopsServiceE devopsServiceE = new DevopsServiceE();
        BeanUtils.copyProperties(devopsServiceReqDTO, devopsServiceE);
        devopsServiceE.setNamespace(devopsEnvironmentE.getCode());
        devopsServiceE.setLabels(gson.toJson(devopsServiceReqDTO.getLabel()));
        devopsServiceE = devopsServiceRepository.insert(devopsServiceE);

        DevopsEnvCommandE devopsEnvCommandE = DevopsEnvCommandFactory.createDevopsEnvCommandE();
        devopsEnvCommandE.setObject(ObjectType.SERVICE.getType());
        devopsEnvCommandE.setObjectId(devopsServiceE.getId());
        devopsEnvCommandE.setCommandType(CommandType.CREATE.getType());
        devopsEnvCommandE.setStatus(CommandStatus.DOING.getStatus());

        insertOrUpdateService(devopsServiceReqDTO,
                devopsServiceE,
                applicationE.getCode(),
                devopsServiceReqDTO.getEnvId(),
                devopsEnvCommandRepository.create(devopsEnvCommandE).getId());
        return true;
    }

    @Override
    public Boolean updateDevopsService(Long projectId, Long id, DevopsServiceReqDTO devopsServiceReqDTO) {
        envUtil.checkEnvConnection(devopsServiceReqDTO.getEnvId(), envListener);
        DevopsServiceValidator.checkService(devopsServiceReqDTO);
        DevopsServiceE devopsServiceE = getDevopsServiceE(id);
        if (!devopsServiceE.getEnvId().equals(devopsServiceReqDTO.getEnvId())) {
            throw new CommonException("error.env.notEqual");
        }
        ApplicationE applicationE = getApplicationE(devopsServiceReqDTO.getAppId());
        String serviceName = devopsServiceReqDTO.getName();
        if (!devopsServiceE.getName().equals(serviceName)) {
            if (!devopsServiceRepository.checkName(
                    projectId, devopsServiceE.getEnvId(), serviceName)) {
                throw new CommonException("error.service.name.check");
            }
            checkOptions(devopsServiceReqDTO.getEnvId(), devopsServiceReqDTO.getAppId(), null, null);
            String oldServiceName = devopsServiceE.getName();

            DevopsEnvCommandE devopsEnvCommandE = devopsEnvCommandRepository
                    .queryByObject(ObjectType.SERVICE.getType(), id);
            updateCommand(devopsEnvCommandE, CommandType.UPDATE.getType(), CommandStatus.DOING.getStatus());
            Long commandId = devopsEnvCommandE.getId();

            updateService(devopsServiceE, devopsServiceReqDTO, applicationE.getCode(), true, commandId);
            idevopsServiceService.delete(oldServiceName,
                    devopsServiceE.getNamespace(),
                    devopsServiceReqDTO.getEnvId(),
                    commandId);

            //更新域名
            List<DevopsIngressPathE> devopsIngressPathEList = devopsIngressRepository.selectByEnvIdAndServiceId(
                    devopsServiceE.getEnvId(), devopsServiceE.getId());
            devopsIngressPathEList.forEach((DevopsIngressPathE dd) ->
                    updateIngressPath(dd, serviceName, devopsServiceReqDTO.getEnvId()));
        } else {
            DevopsEnvCommandE devopsEnvCommandE = devopsEnvCommandRepository
                    .queryByObject(ObjectType.SERVICE.getType(), id);
            updateCommand(devopsEnvCommandE, CommandType.UPDATE.getType(), CommandStatus.DOING.getStatus());
            List<PortMapE> oldPort = devopsServiceE.getPorts();
            if (devopsServiceE.getAppId().equals(devopsServiceReqDTO.getAppId())) {
                //查询网络对应的实例
                List<DevopsServiceAppInstanceE> devopsServiceInstanceEList =
                        devopsServiceInstanceRepository.selectByServiceId(devopsServiceE.getId());
                Boolean isUpdate = !devopsServiceReqDTO.getAppInstance()
                        .retainAll(devopsServiceInstanceEList.stream()
                                .map(DevopsServiceAppInstanceE::getAppInstanceId)
                                .collect(Collectors.toList()));

                if (!isUpdate && oldPort.retainAll(devopsServiceReqDTO.getPorts())
                        && !isUpdateExternalIp(devopsServiceReqDTO, devopsServiceE)) {
                    throw new CommonException("no change!");
                }
            } else {
                checkOptions(devopsServiceE.getEnvId(), devopsServiceReqDTO.getAppId(), null, null);
            }

            updateService(devopsServiceE,
                    devopsServiceReqDTO,
                    applicationE.getCode(),
                    false, devopsEnvCommandE.getId());

            //更新域名
            if (!oldPort.equals(devopsServiceReqDTO.getPorts())) {
                List<DevopsIngressPathE> devopsIngressPathEList = devopsIngressRepository.selectByEnvIdAndServiceId(
                        devopsServiceE.getEnvId(), devopsServiceE.getId());
                devopsIngressPathEList.forEach(t -> updateIngressPath(t, null, devopsServiceReqDTO.getEnvId()));
            }
        }
        return true;
    }

    @Override
    public void deleteDevopsService(Long id) {
        DevopsServiceE devopsServiceE = getDevopsServiceE(id);
        envUtil.checkEnvConnection(devopsServiceE.getEnvId(), envListener);
        devopsServiceE.setStatus(ServiceStatus.OPERATIING.getStatus());
        DevopsEnvCommandE newdevopsEnvCommandE = devopsEnvCommandRepository
                .queryByObject(ObjectType.SERVICE.getType(), id);
        newdevopsEnvCommandE.setCommandType(CommandType.DELETE.getType());
        newdevopsEnvCommandE.setStatus(CommandStatus.DOING.getStatus());
        devopsEnvCommandRepository.update(newdevopsEnvCommandE);
        devopsServiceRepository.update(devopsServiceE);
        //删除service
        idevopsServiceService.delete(
                devopsServiceE.getName(),
                devopsServiceE.getNamespace(),
                devopsServiceE.getEnvId(),
                newdevopsEnvCommandE.getId());
    }

    /**
     * 获取实例
     *
     * @param devopsServiceReqDTO 网络参数
     * @param serviceId           网络id
     * @return String
     */
    private String updateServiceInstanceAndGetCode(DevopsServiceReqDTO devopsServiceReqDTO,
                                                   Long serviceId) {
        StringBuilder stringBuffer = new StringBuilder();
        List<Long> appInstances = devopsServiceReqDTO.getAppInstance();
        if (appInstances.isEmpty()) {
            throw new CommonException("error.param.get");
        }
        appInstances.forEach(appInstance -> {
            checkOptions(devopsServiceReqDTO.getEnvId(), devopsServiceReqDTO.getAppId(),
                    null, appInstance);
            ApplicationInstanceE applicationInstanceE =
                    applicationInstanceRepository.selectById(appInstance);
            if (applicationInstanceE == null) {
                throw new CommonException("error.instance.query");
            }
            DevopsServiceAppInstanceE devopsServiceAppInstanceE = new DevopsServiceAppInstanceE();
            devopsServiceAppInstanceE.setServiceId(serviceId);
            devopsServiceAppInstanceE.setAppInstanceId(appInstance);
            devopsServiceAppInstanceE.setCode(applicationInstanceE.getCode());
            devopsServiceInstanceRepository.insert(devopsServiceAppInstanceE);
            stringBuffer.append(applicationInstanceE.getCode()).append("+");
        });

        return stringBuffer.toString().substring(0, stringBuffer.toString().lastIndexOf('+'));
    }

    /**
     * 校验参数
     *
     * @param envId         环境id
     * @param appId         应用id
     * @param appVersionId  应用版本id
     * @param appInstanceId 应用实例id
     */
    public void checkOptions(Long envId, Long appId, Long appVersionId, Long appInstanceId) {
        if (applicationInstanceRepository.checkOptions(envId, appId, appVersionId, appInstanceId) == 0) {
            throw new CommonException("error.instance.query");
        }
    }

    /**
     * 获取k8s service的yaml格式
     */
    private String getServiceYaml(DevopsServiceReqDTO devopsServiceReqDTO, String namespace,
                                  Map<String, String> labels,
                                  Map<String, String> annotations) {
        V1Service service = new V1Service();
        service.setKind("Service");
        service.setApiVersion("v1");
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(devopsServiceReqDTO.getName());
        metadata.setNamespace(namespace);
        metadata.setLabels(labels);
        metadata.setAnnotations(annotations);
        service.setMetadata(metadata);

        V1ServiceSpec spec = new V1ServiceSpec();
        List<V1ServicePort> ports = devopsServiceReqDTO.getPorts().parallelStream()
                .map(t -> {
                    V1ServicePort v1ServicePort = new V1ServicePort();
                    BeanUtils.copyProperties(t, v1ServicePort);
                    v1ServicePort.setTargetPort(new IntOrString(t.getTargetPort().intValue()));
                    if (t.getName() == null) {
                        v1ServicePort.setName("http" + System.currentTimeMillis());
                    }
                    if (t.getProtocol() == null) {
                        v1ServicePort.setProtocol("TCP");
                    }
                    return v1ServicePort;
                }).collect(Collectors.toList());

        if (!StringUtils.isEmpty(devopsServiceReqDTO.getExternalIp())) {
            List<String> externalIps = new ArrayList<>(
                    Arrays.asList(devopsServiceReqDTO.getExternalIp().split(",")));
            spec.setExternalIPs(externalIps);
        }

        spec.setPorts(ports);
        spec.setSessionAffinity("None");
        spec.type("ClusterIP");
        service.setSpec(spec);

        return gson.toJson(service);
    }

    /**
     * 更新service
     *
     * @param devopsServiceReqDTO 网络参数
     * @param devopsServiceE      网络实例
     * @param envId               环境Id
     * @param commandId           commandId
     * @param appCode             应用code
     */
    public void insertOrUpdateService(DevopsServiceReqDTO devopsServiceReqDTO,
                                      DevopsServiceE devopsServiceE,
                                      String appCode,
                                      Long envId,
                                      Long commandId) {
        String serviceInstances = updateServiceInstanceAndGetCode(devopsServiceReqDTO, devopsServiceE.getId());
        Map<String, String> annotations = new HashMap<>();
        annotations.put("choerodon.io/network-service-instances", serviceInstances);

        String serviceYaml = getServiceYaml(
                devopsServiceReqDTO,
                devopsServiceE.getNamespace(),
                devopsServiceReqDTO.getLabel(),
                annotations);

        DevopsServiceE appDeploy = devopsServiceRepository.query(devopsServiceE.getId());
        appDeploy.setAnnotations(gson.toJson(annotations));
        appDeploy.setObjectVersionNumber(appDeploy.getObjectVersionNumber());
        appDeploy.setStatus(ServiceStatus.OPERATIING.getStatus());
        devopsServiceRepository.update(appDeploy);
        idevopsServiceService.deploy(
                serviceYaml,
                devopsServiceReqDTO.getName(),
                appDeploy.getNamespace(),
                envId,
                commandId);
    }

    /**
     * 更新service
     *
     * @param devopsServiceE      网络实例
     * @param devopsServiceReqDTO 网络参数
     * @param appCode             应用code
     * @param commandId           commandId
     * @param flag                标记
     */
    private void updateService(DevopsServiceE devopsServiceE, DevopsServiceReqDTO devopsServiceReqDTO,
                               String appCode, Boolean flag, Long commandId) {
        if (flag) {
            devopsServiceE.setName(devopsServiceReqDTO.getName());
        }
        devopsServiceE.setAppId(devopsServiceReqDTO.getAppId());
        devopsServiceE.setLabels(gson.toJson(devopsServiceReqDTO.getLabel()));
        devopsServiceE.setPorts(devopsServiceReqDTO.getPorts());
        devopsServiceE.setExternalIp(devopsServiceReqDTO.getExternalIp());
        devopsServiceRepository.update(devopsServiceE);
        List<DevopsServiceAppInstanceE> devopsServiceAppInstanceEList = devopsServiceInstanceRepository
                .selectByServiceId(devopsServiceE.getId());
        for (DevopsServiceAppInstanceE s : devopsServiceAppInstanceEList) {
            devopsServiceInstanceRepository.deleteById(s.getId());
        }
        insertOrUpdateService(devopsServiceReqDTO,
                devopsServiceE,
                appCode, devopsServiceReqDTO.getEnvId(), commandId);
    }

    /**
     * 判断外部ip是否更新
     */
    private Boolean isUpdateExternalIp(DevopsServiceReqDTO devopsServiceReqDTO, DevopsServiceE devopsServiceE) {
        return !((StringUtils.isEmpty(devopsServiceReqDTO.getExternalIp())
                && StringUtils.isEmpty(devopsServiceE.getExternalIp()))
                || (!StringUtils.isEmpty(devopsServiceReqDTO.getExternalIp())
                && !StringUtils.isEmpty(devopsServiceE.getExternalIp())
                && devopsServiceReqDTO.getExternalIp().equals(devopsServiceE.getExternalIp())));
    }

    /**
     * 查询网络信息
     */
    private DevopsServiceE getDevopsServiceE(Long id) {
        DevopsServiceE devopsServiceE = devopsServiceRepository.query(id);
        if (devopsServiceE == null) {
            throw new CommonException("error.service.query");
        }
        return devopsServiceE;
    }

    /**
     * 查询应用
     *
     * @param id 应用id
     * @return app
     */
    public ApplicationE getApplicationE(long id) {
        ApplicationE applicationE = applicationRepository.query(id);
        if (applicationE == null) {
            throw new CommonException("error.application.query");
        }
        return applicationE;
    }

    private void updateCommand(DevopsEnvCommandE devopsEnvCommandE, String type, String status) {
        devopsEnvCommandE.setCommandType(type);
        devopsEnvCommandE.setStatus(status);
        devopsEnvCommandRepository.update(devopsEnvCommandE);
    }

    private void updateIngressPath(DevopsIngressPathE devopsIngressPathE, String serviceName, Long envId) {
        DevopsIngressDO devopsIngressDO = devopsIngressRepository
                .getIngress(devopsIngressPathE.getDevopsIngressE().getId());

        DevopsEnvCommandE newDevopsEnvCommandE = devopsEnvCommandRepository
                .queryByObject(ObjectType.INGRESS.getType(), devopsIngressDO.getId());
        updateCommand(newDevopsEnvCommandE, CommandType.CREATE.getType(), CommandStatus.DOING.getStatus());

        if (serviceName != null) {
            devopsIngressPathE.setServiceName(serviceName);
            devopsIngressRepository.updateIngressPath(devopsIngressPathE);
        }

        DevopsEnvironmentE devopsEnvironmentE = devopsEnviromentRepository
                .queryById(devopsIngressDO.getEnvId());
        V1beta1Ingress v1beta1Ingress = devopsIngressService.createIngress(devopsIngressDO.getDomain(),
                devopsIngressDO.getName(), devopsEnvironmentE.getCode());
        List<DevopsIngressPathE> devopsIngressPathEListTemp = devopsIngressRepository
                .selectByIngressId(devopsIngressDO.getId());
        devopsIngressPathEListTemp.forEach(ddTemp ->
                v1beta1Ingress.getSpec().getRules().get(0).getHttp().addPathsItem(
                        devopsIngressService.createPath(ddTemp.getPath(), ddTemp.getServiceId())));
//        idevopsIngressService.createIngress(gson.toJson(v1beta1Ingress),
//                devopsIngressDO.getName(),
//                devopsEnvironmentE.getCode(), envId, newDevopsEnvCommandE.getId());
    }

}
