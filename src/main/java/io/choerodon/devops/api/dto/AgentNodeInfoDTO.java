package io.choerodon.devops.api.dto;

/**
 * @author zmf
 */
public class AgentNodeInfoDTO {
    public String nodeName;
    public String status;
    public String type;
    public String createTime;
    public String cpuCapacity;
    public String cpuAllocatable;
    public String podAllocatable;
    public String podCapacity;
    public String memoryCapacity;
    public String memoryAllocatable;
    public String memoryRequest;
    public String memoryLimit;
    public String cpuRequest;
    public String cpuLimit;
    public Long podCount;

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getCpuCapacity() {
        return cpuCapacity;
    }

    public void setCpuCapacity(String cpuCapacity) {
        this.cpuCapacity = cpuCapacity;
    }

    public String getCpuAllocatable() {
        return cpuAllocatable;
    }

    public void setCpuAllocatable(String cpuAllocatable) {
        this.cpuAllocatable = cpuAllocatable;
    }

    public String getPodAllocatable() {
        return podAllocatable;
    }

    public void setPodAllocatable(String podAllocatable) {
        this.podAllocatable = podAllocatable;
    }

    public String getPodCapacity() {
        return podCapacity;
    }

    public void setPodCapacity(String podCapacity) {
        this.podCapacity = podCapacity;
    }

    public String getMemoryCapacity() {
        return memoryCapacity;
    }

    public void setMemoryCapacity(String memoryCapacity) {
        this.memoryCapacity = memoryCapacity;
    }

    public String getMemoryAllocatable() {
        return memoryAllocatable;
    }

    public void setMemoryAllocatable(String memoryAllocatable) {
        this.memoryAllocatable = memoryAllocatable;
    }

    public String getMemoryRequest() {
        return memoryRequest;
    }

    public void setMemoryRequest(String memoryRequest) {
        this.memoryRequest = memoryRequest;
    }

    public String getMemoryLimit() {
        return memoryLimit;
    }

    public void setMemoryLimit(String memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    public String getCpuRequest() {
        return cpuRequest;
    }

    public void setCpuRequest(String cpuRequest) {
        this.cpuRequest = cpuRequest;
    }

    public String getCpuLimit() {
        return cpuLimit;
    }

    public void setCpuLimit(String cpuLimit) {
        this.cpuLimit = cpuLimit;
    }

    public Long getPodCount() {
        return podCount;
    }

    public void setPodCount(Long podCount) {
        this.podCount = podCount;
    }
}
