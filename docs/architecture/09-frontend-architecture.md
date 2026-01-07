# Frontend Architecture

This document details the React SPA implementation, OAuth2 PKCE authentication flow, SSE streaming consumption, and build integration.

## Overview

**Technology Stack**:

- React 18.3.1
- Vite 5.4.11
- React Router (navigation)
- React Markdown (message rendering)
- React Syntax Highlighter (code blocks)

**Location**: `mcp-client/src/main/frontend/`
**Build Tool**: Vite (fast HMR, optimized production builds)
**Integration**: Frontend Maven Plugin

---

## Application Structure

```text
src/main/frontend/
├── public/                      # Static assets
│   └── favicon.ico
├── src/
│   ├── main.jsx                 # React entry point
│   ├── index.css                # Global styles
│   ├── App.jsx                  # Main application shell
│   ├── AuthContext.jsx          # Authentication state management
│   └── components/
│       ├── ChatPage.jsx         # Main chat interface
│       ├── LoginPage.jsx        # Login UI
│       ├── Header.jsx           # App header with user menu
│       ├── Sidebar.jsx          # Chat history sidebar
│       └── MessageList.jsx      # Chat message display
├── index.html                   # HTML shell
├── vite.config.js               # Vite configuration
└── package.json                 # npm dependencies
```

---

## Key Components

### App.jsx - Main Application Shell

**Purpose**: Root component, manages authentication state and routing

```javascript
import React from 'react';
import { useAuth } from './AuthContext';
import LoginPage from './components/LoginPage';
import ChatPage from './components/ChatPage';
import Header from './components/Header';

function App() {
  const { user, loading } = useAuth();
  const [theme, setTheme] = React.useState('light');

  const toggleTheme = () => {
    setTheme(theme === 'light' ? 'dark' : 'light');
  };

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  return (
    <div className={`app theme-${theme}`}>
      {user ? (
        <>
          <Header user={user} onToggleTheme={toggleTheme} theme={theme} />
          <ChatPage />
        </>
      ) : (
        <LoginPage />
      )}
    </div>
  );
}

export default App;
```

**Responsibilities**:

- Check authentication status
- Show login page if not authenticated
- Show chat interface if authenticated
- Theme toggle (dark/light mode)
- Header with user profile

### AuthContext.jsx - Authentication State

**Purpose**: Centralized auth state management with React Context

```javascript
import React, { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Check auth status on mount
  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const response = await fetch('/api/auth/status');
      const data = await response.json();

      if (data.authenticated) {
        const userResponse = await fetch('/api/auth/user');
        const userData = await userResponse.json();
        setUser(userData);
      } else {
        setUser(null);
      }
    } catch (error) {
      console.error('Auth check failed:', error);
      setUser(null);
    } finally {
      setLoading(false);
    }
  };

  const login = () => {
    // Redirect to OAuth2 authorization endpoint
    window.location.href = '/oauth2/authorization/frontend-app';
  };

  const logout = async () => {
    try {
      await fetch('/logout', { method: 'POST' });
      setUser(null);
      window.location.href = '/login';
    } catch (error) {
      console.error('Logout failed:', error);
    }
  };

  const value = {
    user,
    loading,
    login,
    logout,
    checkAuthStatus,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
```

**State Management**:

- `user`: Current user object (name, email, roles) or null
- `loading`: Initial auth check in progress
- `login()`: Redirect to OAuth2 login
- `logout()`: Clear session, redirect
- `checkAuthStatus()`: Re-verify authentication

### ChatPage.jsx - Chat Interface

**Purpose**: Main chat UI with message history and input

```javascript
import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';

function ChatPage() {
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);
  const [currentResponse, setCurrentResponse] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!question.trim()) return;

    // Add user message
    const userMessage = { role: 'user', content: question };
    setMessages((prev) => [...prev, userMessage]);
    setQuestion('');
    setLoading(true);
    setCurrentResponse('');

    try {
      // Start SSE stream
      const response = await fetch('/api/v1/resos/stream/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ question }),
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      // Read stream
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let accumulated = '';

      while (true) {
        const { done, value } = await reader.read();

        if (done) {
          break;
        }

        const chunk = decoder.decode(value, { stream: true });

        // Parse SSE format
        const lines = chunk.split('\n');
        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const token = line.substring(6);
            accumulated += token;
            setCurrentResponse(accumulated); // Update UI immediately
          }
        }
      }

      // Add complete response as assistant message
      const assistantMessage = { role: 'assistant', content: accumulated };
      setMessages((prev) => [...prev, assistantMessage]);
      setCurrentResponse('');
    } catch (error) {
      console.error('Chat error:', error);
      setMessages((prev) => [
        ...prev,
        {
          role: 'error',
          content: 'Failed to get response. Please try again.',
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="chat-page">
      <div className="messages">
        {messages.map((msg, index) => (
          <div key={index} className={`message message-${msg.role}`}>
            {msg.role === 'user' && <strong>You:</strong>}
            {msg.role === 'assistant' && <strong>Assistant:</strong>}
            <ReactMarkdown
              components={{
                code({ node, inline, className, children, ...props }) {
                  const match = /language-(\w+)/.exec(className || '');
                  return !inline && match ? (
                    <SyntaxHighlighter style={vscDarkPlus} language={match[1]} PreTag="div" {...props}>
                      {String(children).replace(/\n$/, '')}
                    </SyntaxHighlighter>
                  ) : (
                    <code className={className} {...props}>
                      {children}
                    </code>
                  );
                },
              }}
            >
              {msg.content}
            </ReactMarkdown>
          </div>
        ))}

        {loading && currentResponse && (
          <div className="message message-assistant streaming">
            <strong>Assistant:</strong>
            <ReactMarkdown>{currentResponse}</ReactMarkdown>
            <span className="cursor">▊</span>
          </div>
        )}
      </div>

      <form onSubmit={handleSubmit} className="chat-input">
        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Ask a question about customers, bookings, or feedback..."
          rows={3}
          disabled={loading}
        />
        <button type="submit" disabled={loading || !question.trim()}>
          {loading ? 'Sending...' : 'Send'}
        </button>
      </form>
    </div>
  );
}

export default ChatPage;
```

**Features**:

- **Markdown Rendering**: ReactMarkdown for formatted responses
- **Code Highlighting**: Syntax highlighter for code blocks
- **Streaming Display**: Tokens appear in real-time
- **Loading States**: Visual feedback during streaming
- **Error Handling**: User-friendly error messages

---

## OAuth2 PKCE Flow

### Login Process

**Step 1: User Clicks Login**:

```javascript
const login = () => {
  window.location.href = '/oauth2/authorization/frontend-app';
};
```

**Step 2: Spring Security Redirects**:

```text
1. Generate PKCE parameters:
   - code_verifier: Random 43-128 char string
   - code_challenge: Base64URL(SHA256(code_verifier))

2. Redirect to authorization endpoint:
   http://localhost:8080/oauth2/authorize
     ?response_type=code
     &client_id=frontend-app
     &scope=openid profile email chat.read chat.write
     &redirect_uri=http://localhost:8081/login/oauth2/code/frontend-app
     &code_challenge=ABC123...
     &code_challenge_method=S256
     &state=random-state-value
```

**Step 3: User Authenticates**:

- Auth server shows login page
- User enters username/password
- Auth server validates credentials (BCrypt check)
- Generates authorization code

**Step 4: Authorization Code Redirect**:

```text
http://localhost:8081/login/oauth2/code/frontend-app
  ?code=AUTHORIZATION_CODE
  &state=random-state-value
```

**Step 5: Token Exchange** (Spring Security automatic):

```http
POST /oauth2/token HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=AUTHORIZATION_CODE
&redirect_uri=http://localhost:8081/login/oauth2/code/frontend-app
&client_id=frontend-app
&code_verifier=ORIGINAL_CODE_VERIFIER
```

**Step 6: Tokens Received**:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "opaque-refresh-token",
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "openid profile email chat.read chat.write"
}
```

**Step 7: Session Established**:

- Spring Security creates session cookie
- User redirected to `/` (chat interface)
- Subsequent requests include session cookie
- API calls use Bearer token from session

### Auth Status Endpoint

**Backend** (`AuthController.java`):

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/status")
    public Map<String, Object> getAuthStatus() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null
            && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken);

        return Map.of("authenticated", authenticated);
    }

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal OidcUser user) {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", user.getFullName());
        userInfo.put("email", user.getEmail());
        userInfo.put("roles", user.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(auth -> auth.startsWith("ROLE_"))
            .toList());

        return ResponseEntity.ok(userInfo);
    }
}
```

**React Usage**:

```javascript
// Check if authenticated
const response = await fetch('/api/auth/status');
const { authenticated } = await response.json();

// Get user info
if (authenticated) {
  const userResponse = await fetch('/api/auth/user');
  const user = await userResponse.json();
  // { name: "Admin", email: "admin@example.com", roles: ["ROLE_ADMIN", ...] }
}
```

---

## SSE Streaming Consumption

### Fetch API with Streaming

**React Implementation**:

```javascript
const streamChat = async (question) => {
  const response = await fetch('/api/v1/resos/stream/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ question }),
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  // Get reader for streaming response
  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  let accumulated = '';

  while (true) {
    const { done, value } = await reader.read();

    if (done) {
      console.log('Stream completed');
      break;
    }

    // Decode chunk
    const chunk = decoder.decode(value, { stream: true });

    // SSE format: "data: <content>\n\n"
    const lines = chunk.split('\n');
    for (const line of lines) {
      if (line.startsWith('data: ')) {
        const token = line.substring(6);
        accumulated += token;

        // Update UI immediately
        setCurrentResponse(accumulated);
      }
    }
  }

  return accumulated;
};
```

**SSE Event Format**:

```text
data: Here
data:  are
data:  the
data:  customers:

data:

data: 1. **John Doe**
```

Each `data:` line is one event, received separately.

### EventSource API (Alternative)

**Simpler for GET requests**:

```javascript
const eventSource = new EventSource('/api/stream/chat?question=test');

eventSource.onmessage = (event) => {
  const token = event.data;
  setResponse((prev) => prev + token);
};

eventSource.onerror = (error) => {
  console.error('SSE error:', error);
  eventSource.close();
};

// Close when done
eventSource.addEventListener('complete', () => {
  eventSource.close();
});
```

**Limitation**: Cannot send POST body with EventSource (GET only)

**Solution**: Use Fetch API with streaming (as shown above)

---

## Build Integration

### Frontend Maven Plugin

**Configuration** (`mcp-client/pom.xml`):

```xml
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>2.0.0</version>
    <configuration>
        <nodeVersion>${node.version}</nodeVersion>
        <npmVersion>${npm.version}</npmVersion>
        <workingDirectory>src/main/frontend</workingDirectory>
    </configuration>
    <executions>
        <execution>
            <id>install-node-and-npm</id>
            <goals>
                <goal>install-node-and-npm</goal>
            </goals>
            <phase>generate-resources</phase>
        </execution>
        <execution>
            <id>npm-install</id>
            <goals>
                <goal>npm</goal>
            </goals>
            <phase>generate-resources</phase>
            <configuration>
                <arguments>install</arguments>
            </configuration>
        </execution>
        <execution>
            <id>npm-build</id>
            <goals>
                <goal>npm</goal>
            </goals>
            <phase>generate-resources</phase>
            <configuration>
                <arguments>run build</arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Build Process**:

1. **generate-resources** phase:
   - Install Node.js v23.4.0 to `src/main/frontend/node/`
   - Install npm 10.9.2
   - Run `npm install` (install dependencies from package.json)
   - Run `npm run build` (Vite production build)
2. **Output**: `src/main/frontend/dist/` (Vite build output)
3. **Copy to Resources**:

   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-resources-plugin</artifactId>
       <executions>
           <execution>
               <id>copy-frontend</id>
               <phase>process-resources</phase>
               <goals>
                   <goal>copy-resources</goal>
               </goals>
               <configuration>
                   <outputDirectory>${project.build.directory}/classes/static</outputDirectory>
                   <resources>
                       <resource>
                           <directory>src/main/frontend/dist</directory>
                       </resource>
                   </resources>
               </configuration>
           </execution>
       </executions>
   </plugin>
   ```

4. **Spring Boot Serves**: Static files from `classpath:/static/`

### Vite Configuration

**File**: `src/main/frontend/vite.config.js`

```javascript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/login': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom'],
          markdown: ['react-markdown', 'react-syntax-highlighter'],
        },
      },
    },
  },
});
```

**Dev Server Features**:

- **Port 5173**: Vite dev server with HMR
- **Proxy**: API calls proxied to Spring Boot backend (port 8081)
- **HMR**: Hot Module Replacement for instant updates

**Production Build**:

- **Code Splitting**: vendor.js, markdown.js, app.js
- **Minification**: Terser for JavaScript
- **Tree Shaking**: Remove unused code
- **Asset Optimization**: Images, fonts optimized

---

## CORS Configuration

### Backend CORS Setup

**SecurityConfig** (`mcp-client/src/main/java/me/pacphi/ai/resos/config/SecurityConfig.java`):

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // Allowed origins
    configuration.setAllowedOrigins(List.of(
        "http://localhost:3000",  // Create React App default
        "http://localhost:5173",  // Vite default
        "http://localhost:8081"   // Production (Spring Boot served)
    ));

    // Allowed methods
    configuration.setAllowedMethods(List.of(
        "GET", "POST", "PUT", "DELETE", "OPTIONS"
    ));

    // Allowed headers
    configuration.setAllowedHeaders(List.of("*"));

    // Allow credentials (cookies, auth headers)
    configuration.setAllowCredentials(true);

    // Max age for preflight cache
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);

    return source;
}
```

**Why Needed**:

- Vite dev server runs on port 5173
- Spring Boot backend on port 8081
- Cross-origin requests (different ports)
- Cookies must be allowed (OAuth2 session)

---

## Development Workflow

### Development Mode

**Terminal 1: Spring Boot Backend**:

```bash
cd mcp-client
mvn spring-boot:run -Dspring-boot.run.profiles=openai,dev
# Runs on port 8081
```

**Terminal 2: Vite Dev Server**:

```bash
cd mcp-client/src/main/frontend
npm run dev
# Runs on port 5173 with HMR
```

**Access**: http://localhost:5173 (proxies API calls to 8081)

**Benefits**:

- Hot Module Replacement (instant React updates)
- Fast refresh
- No Maven rebuild for frontend changes

### Production Mode

```bash
cd mcp-client
mvn clean package
java -jar target/spring-ai-resos-mcp-frontend-1.0.0-SNAPSHOT.jar
```

**Access**: http://localhost:8081 (Spring Boot serves static files)

**Benefits**:

- Single JAR deployment
- No separate frontend server
- Simplified deployment

---

## Theme Support

### CSS Variables

**index.css**:

```css
:root {
  --bg-primary: #ffffff;
  --bg-secondary: #f5f5f5;
  --text-primary: #333333;
  --text-secondary: #666666;
  --accent: #007bff;
}

[data-theme='dark'] {
  --bg-primary: #1a1a1a;
  --bg-secondary: #2a2a2a;
  --text-primary: #e0e0e0;
  --text-secondary: #a0a0a0;
  --accent: #4a9eff;
}

body {
  background-color: var(--bg-primary);
  color: var(--text-primary);
}
```

### Theme Toggle

**App.jsx**:

```javascript
const [theme, setTheme] = useState(localStorage.getItem('theme') || 'light');

const toggleTheme = () => {
  const newTheme = theme === 'light' ? 'dark' : 'light';
  setTheme(newTheme);
  localStorage.setItem('theme', newTheme);
  document.documentElement.setAttribute('data-theme', newTheme);
};

useEffect(() => {
  document.documentElement.setAttribute('data-theme', theme);
}, [theme]);
```

**Persistence**: Theme saved to localStorage

---

## State Management

### React State Hooks

**useState for Local State**:

```javascript
const [messages, setMessages] = useState([]);
const [loading, setLoading] = useState(false);
const [error, setError] = useState(null);
```

**useContext for Global State**:

```javascript
const { user, login, logout } = useAuth();
```

**useEffect for Side Effects**:

```javascript
useEffect(() => {
  checkAuthStatus();
}, []); // Run on mount
```

### No External State Management

**Why no Redux/Zustand?**:

- Simple application (limited state)
- Context API sufficient for auth state
- Local state for chat (messages)
- No complex state interactions

**Future Enhancement**: If state grows complex, consider Zustand

---

## Package.json

```json
{
  "name": "spring-ai-resos-frontend",
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "lint": "eslint src --ext js,jsx"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-markdown": "^9.0.2",
    "react-syntax-highlighter": "^15.6.1",
    "remark-gfm": "^4.0.0"
  },
  "devDependencies": {
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.4",
    "eslint": "^9.15.0",
    "eslint-plugin-react": "^7.37.2",
    "eslint-plugin-react-hooks": "^5.0.0",
    "vite": "^5.4.11"
  }
}
```

**Dependencies**:

- **react**: UI framework
- **react-markdown**: Markdown rendering (for AI responses)
- **react-syntax-highlighter**: Code block highlighting
- **remark-gfm**: GitHub Flavored Markdown support

---

## Build Commands

### Development

```bash
# Install dependencies
cd mcp-client/src/main/frontend
npm install

# Start dev server (with HMR)
npm run dev
# Access at http://localhost:5173
```

### Production Build

```bash
# Vite production build
npm run build
# Output: dist/

# Or via Maven
cd mcp-client
mvn clean package
# Runs npm build automatically
```

### Linting

```bash
npm run lint
# Checks ESLint rules for React best practices
```

---

## Security Considerations

### XSS Prevention

**ReactMarkdown** automatically sanitizes HTML:

```javascript
<ReactMarkdown>{userResponse} // Safe, HTML escaped</ReactMarkdown>
```

**Dangerous Pattern** (don't do this):

```javascript
<div dangerouslySetInnerHTML={{ __html: userResponse }} /> // ❌ XSS risk
```

### CSRF Protection

**Backend** (cookie-based CSRF token):

```java
.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
)
```

**React**: CSRF token automatically included in same-origin requests

### Content Security Policy (Future)

```http
Content-Security-Policy:
  default-src 'self';
  script-src 'self';
  style-src 'self' 'unsafe-inline';
  img-src 'self' data: https:;
  connect-src 'self' https://api.openai.com;
```

---

## Critical Files

| File                                                       | Purpose                      | Lines |
| ---------------------------------------------------------- | ---------------------------- | ----- |
| `mcp-client/src/main/frontend/src/App.jsx`                 | Main application shell       | ~100  |
| `mcp-client/src/main/frontend/src/AuthContext.jsx`         | Authentication state         | ~80   |
| `mcp-client/src/main/frontend/src/components/ChatPage.jsx` | Chat interface               | ~200  |
| `mcp-client/src/main/frontend/vite.config.js`              | Vite configuration           | ~40   |
| `mcp-client/src/main/frontend/package.json`                | npm dependencies             | ~30   |
| `mcp-client/pom.xml`                                       | Frontend Maven plugin config | ~50   |

## Related Documentation

- [AI Integration](08-ai-integration.md) - SSE streaming from backend
- [Security Architecture](06-security-architecture.md) - OAuth2 PKCE flow
- [Module Architecture](03-module-architecture.md) - MCP client module details
