/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.job.cloud.state.ready;

import com.dangdang.ddframe.job.cloud.JobContext;
import com.dangdang.ddframe.job.cloud.config.CloudJobConfiguration;
import com.dangdang.ddframe.job.cloud.config.ConfigurationService;
import com.dangdang.ddframe.job.cloud.state.SequentialJob;
import com.dangdang.ddframe.job.cloud.state.misfired.MisfiredService;
import com.dangdang.ddframe.job.cloud.state.running.RunningService;
import com.dangdang.ddframe.reg.base.CoordinatorRegistryCenter;
import com.google.common.base.Optional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 待运行作业队列服务.
 *
 * @author zhangliang
 */
public class ReadyService {
    
    private final CoordinatorRegistryCenter registryCenter;
    
    private final ConfigurationService configService;
    
    private final RunningService runningService;
    
    private final MisfiredService misfiredService;
    
    public ReadyService(final CoordinatorRegistryCenter registryCenter) {
        this.registryCenter = registryCenter;
        configService = new ConfigurationService(registryCenter);
        runningService = new RunningService(registryCenter);
        misfiredService = new MisfiredService(registryCenter);
    }
    
    /**
     * 将作业放入待执行队列.
     * 
     * @param jobName 作业名称
     */
    public void add(final String jobName) {
        registryCenter.persistSequential(ReadyNode.getReadyJobNodePath(jobName), "");
    }
    
    /**
     * 从待执行队列中获取所有有资格执行的作业上下文.
     *
     * @param ineligibleJobContexts 无资格执行的作业上下文
     * @return 有资格执行的作业上下文集合
     */
    public Map<String, JobContext> getAllEligibleJobContexts(final Collection<JobContext> ineligibleJobContexts) {
        if (!registryCenter.isExisted(ReadyNode.ROOT)) {
            return Collections.emptyMap();
        }
        List<String> jobNamesWithSequential = registryCenter.getChildrenKeys(ReadyNode.ROOT);
        Map<String, JobContext> result = new HashMap<>(jobNamesWithSequential.size(), 1);
        for (String each : jobNamesWithSequential) {
            SequentialJob sequentialJob = new SequentialJob(each);
            String jobName = sequentialJob.getJobName();
            Optional<CloudJobConfiguration> jobConfig = configService.load(jobName);
            if (!jobConfig.isPresent()) {
                registryCenter.remove(ReadyNode.getReadyJobNodePath(each));
                continue;
            }
            if (runningService.isJobRunning(jobName)) {
                misfiredService.add(jobName);
                continue;
            }
            if (!result.containsKey(jobName) && !ineligibleJobContexts.contains(jobName)) {
                result.put(each, JobContext.from(jobConfig.get()));
            }
        }
        return result;
    }
    
    /**
     * 从待执行队列中删除相关作业.
     *
     * @param jobNamesWithSequential 待删除的作业名集合
     */
    public void remove(final Collection<String> jobNamesWithSequential) {
        for (String each : jobNamesWithSequential) {
            registryCenter.remove(ReadyNode.getReadyJobNodePath(each));
        }
    }
}
