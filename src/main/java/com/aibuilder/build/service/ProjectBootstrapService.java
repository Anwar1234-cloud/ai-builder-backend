package com.aibuilder.build.service;

import com.aibuilder.project.entity.Project;
import com.aibuilder.project.repository.ProjectRepository;
import com.aibuilder.user.entity.User;
import com.aibuilder.user.repository.UserRepository;
import com.aibuilder.workspace.entity.ProjectFile;
import com.aibuilder.workspace.repository.ProjectFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectBootstrapService {

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final UserRepository userRepository;

    @Transactional
    public void bootstrapReactProject(Long projectId) {

        User user = getAuthenticatedUser();

        Project project = getOwnedProject(projectId, user);

        createIfMissing(
                project,
                "package.json",
                """
                {
                  "name": "ai-builder-app",
                  "private": true,
                  "version": "0.0.0",
                  "type": "module",
                  "scripts": {
                    "dev": "vite",
                    "build": "vite build",
                    "preview": "vite preview"
                  },
                  "dependencies": {
                    "react": "19.3.0",
                    "react-dom": "19.3.0"
                  },
                  "devDependencies": {
                    "@vitejs/plugin-react": "6.1.1",
                    "vite": "8.2.2"
                  }
                }
                """,
                "json"
        );

        createIfMissing(
                project,
                "vite.config.js",
                """
                import { defineConfig } from 'vite';
                import react from '@vitejs/plugin-react';

                export default defineConfig({
                  plugins: [react()]
                });
                """,
                "javascript"
        );

        createIfMissing(
                project,
                "index.html",
                """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>AI Builder App</title>
                  </head>
                  <body>
                    <div id="root"></div>
                    <script type="module" src="/src/main.jsx"></script>
                  </body>
                </html>
                """,
                "html"
        );

        createIfMissing(
                project,
                "src/main.jsx",
                """
                import { StrictMode } from 'react';
                import { createRoot } from 'react-dom/client';
                import App from './App.jsx';

                createRoot(document.getElementById('root')).render(
                  <StrictMode>
                    <App />
                  </StrictMode>
                );
                """,
                "jsx"
        );
    }

    private void createIfMissing(
            Project project,
            String path,
            String content,
            String language
    ) {

        boolean exists =
                projectFileRepository
                        .existsByProjectIdAndPath(
                                project.getId(),
                                path
                        );

        if (exists) {
            return;
        }

        ProjectFile file = new ProjectFile();

        file.setProject(project);
        file.setPath(path);
        file.setContent(content);
        file.setLanguage(language);

        projectFileRepository.save(file);
    }

    private User getAuthenticatedUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new RuntimeException(
                    "User is not authenticated"
            );
        }

        return userRepository
                .findByEmail(
                        authentication.getName().toLowerCase()
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Authenticated user not found"
                        )
                );
    }

    private Project getOwnedProject(
            Long projectId,
            User user
    ) {

        Project project =
                projectRepository.findById(projectId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Project not found: " + projectId
                                )
                        );

        if (!project.getUser().getId()
                .equals(user.getId())) {

            throw new RuntimeException(
                    "You do not have access to this project"
            );
        }

        return project;
    }
}