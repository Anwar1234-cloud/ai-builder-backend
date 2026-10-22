
package com.aibuilder.fullstack.service;

import com.aibuilder.agent.entity.AgentRun;
import com.aibuilder.agent.repository.AgentRunRepository;
import com.aibuilder.fullstack.dto.FullStackPlan;
import com.aibuilder.fullstack.entity.FullStackArchitecture;
import com.aibuilder.fullstack.repository.FullStackArchitectureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FullStackArchitectureService {

    private final FullStackArchitectureRepository repository;
    private final AgentRunRepository agentRunRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void savePlan(
            Long agentRunId,
            FullStackPlan plan
    ) {

        if (plan == null) {
            throw new IllegalArgumentException(
                    "Full-stack plan cannot be null"
            );
        }

        AgentRun agentRun = agentRunRepository.findById(agentRunId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Agent run not found: " + agentRunId
                        )
                );

        FullStackArchitecture architecture = repository
                .findByAgentRunId(agentRunId)
                .orElseGet(FullStackArchitecture::new);

        architecture.setAgentRun(agentRun);
        architecture.setProject(agentRun.getProject());
        architecture.setPlanJson(
                objectMapper.writeValueAsString(plan)
        );

        if (architecture.getCreatedAt() == null) {
            architecture.setCreatedAt(LocalDateTime.now());
        }

        repository.save(architecture);
    }

    @Transactional(readOnly = true)
    public FullStackPlan getPlan(Long agentRunId) {

        FullStackArchitecture architecture = repository
                .findByAgentRunId(agentRunId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Architecture not found for agent run: "
                                        + agentRunId
                        )
                );

        return objectMapper.readValue(
                architecture.getPlanJson(),
                FullStackPlan.class
        );
    }
}
