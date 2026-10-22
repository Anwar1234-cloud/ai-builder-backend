
package com.aibuilder.deployment.service;

import com.aibuilder.deployment.railway.RailwayCliDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RailwayBackendUrlService {

    private final RailwayCliDomainService railwayCliDomainService;

    public String resolveBackendUrl(
            String projectId,
            String environmentId,
            String serviceId
    ) {
        return railwayCliDomainService.getExistingDomain(
                projectId,
                environmentId,
                serviceId
        );
    }
}
