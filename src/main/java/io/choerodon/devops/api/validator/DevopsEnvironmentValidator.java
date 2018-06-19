package io.choerodon.devops.api.validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.domain.application.repository.ApplicationInstanceRepository;
import io.choerodon.devops.domain.application.repository.DevopsIngressRepository;
import io.choerodon.devops.domain.application.repository.DevopsServiceRepository;

/**
 * Creator: Runge
 * Date: 2018/6/13
 * Time: 11:25
 * Description:
 */
@Component
public class DevopsEnvironmentValidator {
    @Autowired
    private ApplicationInstanceRepository applicationInstanceRepository;
    @Autowired
    private DevopsIngressRepository devopsIngressRepository;
    @Autowired
    private DevopsServiceRepository devopsServiceRepository;

    public void checkEnvCanDisabled(Long envId) {
        if (applicationInstanceRepository.selectByEnvId(envId) > 0) {
            throw new CommonException("error.env.stop.instanceExist");
        }
        if (devopsServiceRepository.checkEnvHasService(envId)) {
            throw new CommonException("error.env.stop.serviceExist");
        }
        if (devopsIngressRepository.checkEnvHasIngress(envId)) {
            throw new CommonException("error.env.stop.IngressExist");
        }
    }
}