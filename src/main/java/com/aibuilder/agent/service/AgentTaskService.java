package com.aibuilder.agent.service;

import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.entity.AgentTask;
import com.aibuilder.agent.entity.AgentTaskStatus;
import com.aibuilder.agent.repository.AgentRunRepository;
import com.aibuilder.agent.repository.AgentTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final AgentTaskRepository agentTaskRepository;
    private final AgentRunRepository agentRunRepository;


    @Transactional
    public AgentTask createTask(
            Long agentRunId,
            String title,
            String description,
            Integer taskOrder
    ) {

        AgentRun agentRun = agentRunRepository.findById(agentRunId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Agent run not found: " + agentRunId
                        )
                );

        AgentTask task = new AgentTask();

        task.setAgentRun(agentRun);
        task.setTitle(title);
        task.setDescription(description);
        task.setTaskOrder(taskOrder);
        task.setStatus(AgentTaskStatus.PENDING);

        return agentTaskRepository.save(task);
    }


    @Transactional
    public AgentTask startTask(Long taskId) {

        AgentTask task = getTask(taskId);

        task.setStatus(AgentTaskStatus.RUNNING);
        task.setStartedAt(
                java.time.LocalDateTime.now()
        );

        return agentTaskRepository.save(task);
    }


    @Transactional
    public AgentTask completeTask(Long taskId) {

        AgentTask task = getTask(taskId);

        task.setStatus(AgentTaskStatus.COMPLETED);
        task.setCompletedAt(
                java.time.LocalDateTime.now()
        );
        task.setErrorMessage(null);

        return agentTaskRepository.save(task);
    }


    @Transactional
    public AgentTask failTask(
            Long taskId,
            String errorMessage
    ) {

        AgentTask task = getTask(taskId);

        task.setStatus(AgentTaskStatus.FAILED);
        task.setCompletedAt(
                java.time.LocalDateTime.now()
        );
        task.setErrorMessage(errorMessage);

        return agentTaskRepository.save(task);
    }


    @Transactional(readOnly = true)
    public AgentTask getTask(Long taskId) {

        return agentTaskRepository.findById(taskId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Agent task not found: " + taskId
                        )
                );
    }


    @Transactional(readOnly = true)
    public List<AgentTask> getTasks(Long agentRunId) {

        agentRunRepository.findById(agentRunId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Agent run not found: " + agentRunId
                        )
                );

        return agentTaskRepository
                .findByAgentRunIdOrderByTaskOrderAsc(agentRunId);
    }

    @Transactional
    public List<AgentTask> createTasksFromPlan(
            Long agentRunId,
            List<com.aibuilder.ai.dto.AgentPlan.PlannedTask> plannedTasks
    ) {

        List<AgentTask> tasks = new java.util.ArrayList<>();

        for (com.aibuilder.ai.dto.AgentPlan.PlannedTask plannedTask
                : plannedTasks) {

            AgentTask task = createTask(
                    agentRunId,
                    plannedTask.title(),
                    plannedTask.description(),
                    plannedTask.taskOrder()
            );

            tasks.add(task);
        }

        return tasks;
    }
}