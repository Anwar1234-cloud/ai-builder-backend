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


    @Transactional
    public void bootstrapFullStackProject(Long projectId) {

        User user = getAuthenticatedUser();

        Project project = getOwnedProject(projectId, user);

        createIfMissing(
                project,
                "frontend/package.json",
                """
                {
                  "name": "generated-fullstack-frontend",
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
                "frontend/vite.config.js",
                """
                import { defineConfig } from 'vite';
                import react from '@vitejs/plugin-react';
    
                export default defineConfig({
                  plugins: [react()],
                  server: {
                    proxy: {
                      '/api': 'http://localhost:8081'
                    }
                  }
                });
                """,
                "javascript"
        );

        createIfMissing(
                project,
                "frontend/index.html",
                """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport"
                          content="width=device-width, initial-scale=1.0" />
                    <title>Generated Full-Stack App</title>
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
                "frontend/src/main.jsx",
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

        createIfMissing(
                project,
                "frontend/src/App.jsx",
                """
                export default function App() {
                  return (
                    <main>
                      <h1>Full-Stack Application</h1>
                      <p>Your application is being generated.</p>
                    </main>
                  );
                }
                """,
                "jsx"
        );

        createIfMissing(
                project,
                "backend/pom.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    
                  <modelVersion>4.0.0</modelVersion>
    
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.5.6</version>
                    <relativePath/>
                  </parent>
    
                  <groupId>com.generated</groupId>
                  <artifactId>generated-app</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
    
                  <properties>
                    <java.version>21</java.version>
                  </properties>
    
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
    
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-data-jpa</artifactId>
                    </dependency>
    
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-validation</artifactId>
                    </dependency>
    
                    <dependency>
                      <groupId>org.postgresql</groupId>
                      <artifactId>postgresql</artifactId>
                      <scope>runtime</scope>
                    </dependency>
    
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-test</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
    
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-maven-plugin</artifactId>
                      </plugin>
                    </plugins>
                  </build>
    
                </project>
                """,
                "xml"
        );

        createIfMissing(
                project,
                "backend/src/main/java/com/generated/app/GeneratedApplication.java",
                """
                package com.generated.app;
    
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
    
                @SpringBootApplication
                public class GeneratedApplication {
    
                    public static void main(String[] args) {
                        SpringApplication.run(
                                GeneratedApplication.class,
                                args
                        );
                    }
                }
                """,
                "java"
        );

        createIfMissing(
                project,
                "backend/src/main/resources/application.yaml",
                """
                server:
                  port: ${PORT:8081}
    
                spring:
                  datasource:
                    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/generated_app}
                    username: ${DATABASE_USERNAME:postgres}
                    password: ${DATABASE_PASSWORD:}
                  jpa:
                    hibernate:
                      ddl-auto: update
                    open-in-view: false
                """,
                "yaml"
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