package io.choerodon.devops.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import io.choerodon.devops.infra.dataobject.DevopsGitlabCommitDO;
import io.choerodon.mybatis.common.BaseMapper;

public interface DevopsGitlabCommitMapper extends BaseMapper<DevopsGitlabCommitDO> {
    List<DevopsGitlabCommitDO> listCommitsByAppId(@Param("appIds") Long[] appId);
}
