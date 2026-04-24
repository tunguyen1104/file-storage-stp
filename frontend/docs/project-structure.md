# 📚 Next.js File Storage Project

## 1. 🗂️ Directory Structure

```
frontend/
├── src/
│   ├── app/                          # Next.js 15 App Router
│   │   ├── (auth)/                   # Authentication route group (own layout, no sidebar)
│   │   │   ├── layout.tsx            #    → Auth layout (no sidebar/header)
│   │   │   ├── login/                #    → Login page
│   │   │   ├── forgot-password/      #    → Forgot password page
│   │   │   ├── reset-password/       #    → Reset password page
│   │   │   ├── verify/               #    → Email verification page
│   │   │   └── check-email/          #    → Check email notification page
│   │   │
│   │   ├── (dashboard)/              # Main route group (requires authentication)
│   │   │   ├── layout.tsx            #    → Dashboard layout (sidebar, header, breadcrumbs)
│   │   │   ├── files/                #    → File management
│   │   │   │   └── [fileId]/         #    → File detail page
│   │   │   ├── upload/               #    → File upload
│   │   │   ├── users/                #    → User management (admin)
│   │   │   ├── tenants/              #    → Tenant management (super admin)
│   │   │   ├── audit-logs/           #    → Activity history
│   │   │   ├── search/               #    → File search
│   │   │   └── unauthorized/         #    → Access denied page
│   │   │
│   │   ├── api/                      # Backend For Frontend (BFF) - API Proxy
│   │   │   ├── [...path]/route.ts    #    → Catch-all proxy: forwards requests to real backend
│   │   │   ├── health/route.ts       #    → Health check endpoint
│   │   │   └── _lib/proxy.ts         #    → Main proxy logic (handles tokens, CSRF, cookie remapping)
│   │   │
│   │   ├── layout.tsx                # Root layout (wraps AuthProvider, ThemeProvider)
│   │   ├── globals.css               # Tailwind CSS styles
│   │   ├── theme.css                 # CSS theme variables
│   │   └── not-found.tsx             # Custom 404 page
│   │
│   ├── middleware.ts                 # Auth check on every request (edge runtime)
│   │
│   ├── contexts/
│   │   └── auth-context.tsx          # React Context managing current user state
│   │
│   ├── hooks/
│   │   ├── use-auth.ts               # Hook to consume auth context in any component
│   │   ├── use-debounce.ts           # Debounce hook for search inputs
│   │   └── use-mobile.ts             # Detect mobile viewport
│   │
│   ├── lib/
│   │   ├── api-client.ts             # Axios instance (auto sends cookies, auto refresh on code 1002)
│   │   ├── api/
│   │   │   ├── auth.ts               # Auth API functions (signIn, logOut, getMe, etc.)
│   │   │   └── core/
│   │   │       └── api-error.ts      # AppError class + toAppError() — normalizes all errors
│   │   ├── auth.ts                   # Auth helpers: DecodedToken, hasRole(), maskEmail()
│   │   ├── csrf.ts                   # CSRF protection utilities (double-submit cookie pattern)
│   │   ├── constants.ts              # Constants: roles, API routes (/api/v1), file limits
│   │   ├── swr-config.ts             # Global SWR configuration (dedup interval, retry, keepPreviousData)
│   │   ├── swr-fetcher.ts            # Default SWR fetcher — unwraps ApiResponse envelope
│   │   ├── utils.ts                  # Shared utility functions (cn, etc.)
│   │   └── schemas/                  # Zod validation schemas
│   │
│   ├── types/
│   │   ├── api.types.ts              # Shared types: ApiResponse<T>, PageResponse<T>
│   │   ├── css.d.ts                  # CSS module type declarations
│   │   ├── auth/
│   │   │   └── auth.ts               # Auth request types: SignInRequest, ResetPasswordRequest
│   │   └── tenant/
│   │       └── tenant.ts             # Tenant-related types
│   │
│   ├── components/                   # UI components (buttons, forms, layout, ...)
│   └── ...
│
├── .env.local                        # Environment variables template
├── package.json                      # Dependencies + "mock" / "dev:mock" scripts
└── next.config.ts                    # Next.js config
```

### 📌 Understanding Route Groups (auth) and (dashboard)

- `(auth)`: This folder **does not create a URL prefix**. Pages inside are still `/login`, `/forgot-password`, ...
- `(dashboard)`: Similarly, no prefix is created. Pages are still `/files`, `/users`, ...
- Purpose: Group routes together for easier layout management. Auth pages don't need sidebar/header, dashboard pages do.

---

## 2. 🔐 Authentication Architecture

### 2.1. Overview of the Flow

The project uses **JWT-based authentication** with the following characteristics:

1. **Tokens are stored in httpOnly cookies** (secure, cannot be read by XSS)
2. **BFF Pattern** (Backend For Frontend): Next.js API routes act as a proxy between client and real backend
3. **Middleware checks permissions** before allowing access to each page
4. **Auto refresh token** when access token expires

---

### 2.2. Main Components

#### **A. Middleware (`src/middleware.ts`)**

**Role**: Intercepts all requests to the application, checks if user is logged in and has access permissions.

**How it works**:

```typescript
// 1. Get access token from cookie
const accessToken = request.cookies.get("accessToken")?.value;

// 2. If NOT logged in and accessing auth-required page → redirect /login
if (!accessToken) {
  if (PUBLIC_ROUTES.some(route => pathname.startsWith(route))) {
    return NextResponse.next(); // Allow access to public pages
  }
  return NextResponse.redirect(new URL("/login", request.url));
}

// 3. Verify token (check signature and expiry)
const secret = new TextEncoder().encode(JWT_SECRET);
const { payload } = await jwtVerify(accessToken, secret);
decoded = payload as DecodedToken;

// 4. If ALREADY logged in but accessing auth page → redirect /files
if (PUBLIC_ROUTES.some(route => pathname.startsWith(route))) {
  return NextResponse.redirect(new URL("/files", request.url));
}

// 5. Check role-based access control (RBAC)
const allowedRoutes = ROLE_ROUTE_MAP[decoded.role] ?? [];
const isAllowed = allowedRoutes.some(route => pathname.startsWith(route));
if (!isAllowed) {
  return NextResponse.redirect(new URL("/unauthorized", request.url));
}
```

**Routes that don't require authentication** (PUBLIC_ROUTES):
- `/login`
- `/forgot-password`
- `/verify`
- `/check-email`
- `/reset-password`

**Role-based permissions** (ROLE_ROUTE_MAP):

| Role | Access Permissions |
|------|-------------------|
| `SUPER_ADMIN` | All (files, upload, search, audit-logs, tenants, users) |
| `TENANT_ADMIN` | Everything except `/tenants` |
| `EDITOR` | files, upload, search |
| `VIEWER` | files, search (read-only) |

---

#### **B. Auth Context (`src/contexts/auth-context.tsx`)**

**Role**: Stores current user information and provides auth functions (login, logout) to the entire app.

**How to use**:

```typescript
// In a component
import { useAuth } from '@/hooks/use-auth';

function MyComponent() {
  const { user, signIn, logOut, isLoading } = useAuth();
  
  if (isLoading) return <div>Loading...</div>;
  
  return (
    <div>
      <p>Hello {user?.fullName}</p>
      <button onClick={logOut}>Sign Out</button>
    </div>
  );
}
```

**How it works**:

1. **Fetch user info**: Uses SWR to call `/api/auth/me` to get current user information
2. **Auto revalidate**: When tab is focused again, does **NOT** automatically refetch (to avoid spamming backend)
3. **No fetch on auth pages**: Checks `pathname` to not call API `/me` on login/forgot-password pages

```typescript
const shouldFetchUser = useMemo(() => {
  // Don't fetch user on auth pages
  if (AUTH_PAGES.some(route => pathname.startsWith(route))) return false;
  return true;
}, [pathname]);

const { data, mutate } = useSWR<AuthUser | null>(
  shouldFetchUser ? API_ROUTES.AUTH.ME : null,
  meFetcher,
  {
    revalidateOnFocus: false, // Don't auto-refetch on focus
    shouldRetryOnError: false, // Don't retry on error
  }
);
```

**The `signIn` function**:

```typescript
const signIn = async (email: string, password: string) => {
  // 1. Call login API (BFF will forward to backend)
  await apiClient.post(API_ROUTES.AUTH.LOGIN, { email, password });
  
  // 2. Backend returns Set-Cookie header, browser auto-saves cookies
  // 3. Refresh user info
  await mutate();
  
  // 4. Redirect to main page
  router.push("/files");
};
```

**The `logOut` function**:

```typescript
const logOut = async () => {
  try {
    // 1. Call logout API (backend will invalidate refresh token)
    await apiClient.post(API_ROUTES.AUTH.LOGOUT);
  } catch {
    // Ignore errors, BFF will clear cookies anyway
  } finally {
    // 2. Clear user cache
    await mutate(null, false);
    
    // 3. Redirect to login
    router.push("/login");
  }
};
```

**Forced logout** (when token expires and cannot be refreshed):

```typescript
useEffect(() => {
  const handleGlobalLogout = () => void logOut();
  window.addEventListener("auth:logout", handleGlobalLogout);
  return () => window.removeEventListener("auth:logout", handleGlobalLogout);
}, [logOut]);
```

---

#### **C. API Client (`src/lib/api-client.ts`)**

**Role**: Axios instance with interceptors to automatically refresh tokens when encountering 401 errors.

**Request Interceptor** (sends CSRF token):

```typescript
apiClient.interceptors.request.use((config) => {
  // Token is sent automatically via cookie (withCredentials: true)
  
  // Send CSRF token for state-changing requests
  const method = config.method?.toUpperCase();
  if (["POST", "PUT", "PATCH", "DELETE"].includes(method)) {
    const csrfToken = getCsrfToken(); // Read from cookie
    config.headers.set("X-CSRF-Token", csrfToken);
  }
  
  return config;
});
```

**Response Interceptor** (auto refresh token):

```typescript
apiClient.interceptors.response.use(
  response => response,
  async (error: AxiosError) => {
    const originalRequest = error.config;
    
    // If not 401 or already retried → throw error
    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }
    
    // If already refreshing → wait for it to complete
    if (refreshPromise) {
      await refreshPromise;
      return apiClient(originalRequest);
    }
    
    // Start token refresh
    refreshPromise = (async () => {
      try {
        await axios.post("/api/auth/refresh", {}, { withCredentials: true });
      } catch (refreshError) {
        // If refresh also returns 401 → force logout
        if (refreshError.response?.status === 401) {
          window.dispatchEvent(new Event("auth:logout"));
        }
        throw refreshError;
      }
    })();
    
    await refreshPromise;
    refreshPromise = null;
    
    // Retry original request with new token
    return apiClient(originalRequest);
  }
);
```

**Why use `refreshPromise`?**

- When multiple requests encounter 401 simultaneously, only 1 refresh token request is made
- Other requests wait for that promise to complete, then retry

---

#### **D. BFF Proxy (`src/app/api/[...path]/route.ts` + `src/app/api/_lib/proxy.ts`)**

**Role**: Acts as a "middleman" between frontend and real backend, handling:
- Extracting token from httpOnly cookie → attaching to `Authorization` header
- Validating CSRF token
- Forwarding requests to backend

**Why do we need BFF?**

1. **Security**: Token stored in httpOnly cookie, JavaScript cannot read it → prevents XSS
2. **Simplifies frontend**: Frontend only needs to call `/api/*`, doesn't need to know backend URL
3. **CSRF protection**: Middleware validates CSRF token before forwarding

**How it works** (file `proxy.ts`):

```typescript
export async function proxyToBackend(req: NextRequest) {
  const pathname = req.nextUrl.pathname;
  
  // 1. SSRF Protection: Only allow whitelisted routes
  const ALLOWED_PREFIXES = ["/api/auth/", "/api/files", "/api/users", ...];
  if (!ALLOWED_PREFIXES.some(prefix => pathname.startsWith(prefix))) {
    return NextResponse.json({ message: "Forbidden" }, { status: 403 });
  }
  
  // 2. CSRF Protection for POST/PUT/PATCH/DELETE
  if (["POST", "PUT", "PATCH", "DELETE"].includes(req.method)) {
    const csrfCookie = cookieStore.get("csrf-token")?.value;
    const csrfHeader = req.headers.get("X-CSRF-Token");
    
    if (!validateCsrfToken(csrfCookie, csrfHeader)) {
      return NextResponse.json({ message: "Invalid CSRF token" }, { status: 403 });
    }
  }
  
  // 3. Get token from cookie
  const accessToken = cookieStore.get("accessToken")?.value;
  const refreshToken = cookieStore.get("refreshToken")?.value;
  
  // 4. Build backend URL
  const url = new URL(pathname, BACKEND_URL);
  const headers = new Headers();
  
  // 5. Attach token to Authorization header
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  
  // 6. Forward cookies (so backend can rotate refresh token)
  headers.set("Cookie", `accessToken=${accessToken}; refreshToken=${refreshToken}`);
  
  // 7. Call backend
  const backendRes = await fetch(url.toString(), {
    method: req.method,
    headers,
    body: req.method !== "GET" ? await req.arrayBuffer() : undefined,
  });
  
  // 8. Forward Set-Cookie from backend to client
  const response = new NextResponse(await backendRes.arrayBuffer(), {
    status: backendRes.status,
  });
  
  const setCookies = backendRes.headers.getSetCookie();
  for (const cookie of setCookies) {
    response.headers.append("Set-Cookie", cookie);
  }
  
  return response;
}
```

**Special handling for logout**:

- If backend is unavailable, still clear cookies on frontend so user can logout
- Retry up to 2 times to ensure backend invalidates refresh token

---

#### **E. CSRF Protection (`src/lib/csrf.ts`)**

**Role**: Prevents CSRF attacks using "double-submit cookie pattern".

**How it works**:

1. **Server generates random CSRF token** and sets it in a cookie (readable by JS)
2. **Client reads the cookie** and sends token via header `X-CSRF-Token`
3. **Server compares** cookie value and header value → if they match, it's OK

**Why is this secure?**

- Attacker can trigger requests with cookies (auto-attached)
- But attacker **CANNOT read cookies** from a different domain (same-origin policy)
- → Attacker cannot set the correct header value

```typescript
// Client-side (api-client.ts)
const csrfToken = getCsrfToken(); // Read from cookie
config.headers.set("X-CSRF-Token", csrfToken);

// Server-side (proxy.ts)
const csrfCookie = cookieStore.get("csrf-token")?.value;
const csrfHeader = req.headers.get("X-CSRF-Token");

if (!validateCsrfToken(csrfCookie, csrfHeader)) {
  return NextResponse.json({ message: "Invalid CSRF token" }, { status: 403 });
}
```

---

### 2.3. Auth-Related Files/Components

| File | Role |
|------|------|
| `src/middleware.ts` | ⚠️ **Critical**: Auth check + permissions for every request |
| `src/contexts/auth-context.tsx` | Manages current user state, provides `signIn` / `logOut` |
| `src/hooks/use-auth.ts` | Hook to use auth context in components |
| `src/lib/api-client.ts` | Axios instance with auto refresh token |
| `src/lib/auth.ts` | Helper functions: `hasRole`, `maskEmail` |
| `src/lib/csrf.ts` | CSRF protection utilities |
| `src/app/api/_lib/proxy.ts` | BFF proxy logic (extracts token from cookie → attaches to header) |
| `src/app/(auth)/login/page.tsx` | Login page |
| `src/app/(auth)/forgot-password/page.tsx` | Forgot password page |
| `src/app/(auth)/reset-password/page.tsx` | Reset password page |

---

### 2.4. How Tokens/Sessions are Stored

**Access Token & Refresh Token**:

- **Stored in httpOnly cookies** (cannot be read via JavaScript)
- Set by backend via `Set-Cookie` header
- BFF proxy automatically attaches to `Authorization` header when calling backend

**CSRF Token**:

- Stored in **readable cookie** (so client can read it)
- Client sends via header `X-CSRF-Token`

**Cookie attributes**:

```typescript
{
  httpOnly: true,        // Access/Refresh token: cannot be read via JS
  secure: true,          // Only send over HTTPS (production)
  sameSite: "lax",       // Basic CSRF protection
  path: "/",             // Applies to entire site
  maxAge: 15 * 60        // Access token: 15 minutes
}
```

---

### 2.5. How Protected Routes Work

**Middleware** checks:

1. Is user logged in? (is `accessToken` in cookie?)
2. Is token still valid? (verify JWT signature + expiry)
3. Does user have access to this route? (compare role with `ROLE_ROUTE_MAP`)

**Example**:

```typescript
// VIEWER can only access /files and /search
const ROLE_ROUTE_MAP = {
  VIEWER: ["/files", "/search", "/unauthorized"],
  EDITOR: ["/files", "/upload", "/search", "/unauthorized"],
  // ...
};

// Middleware check
const allowedRoutes = ROLE_ROUTE_MAP[decoded.role] ?? [];
const isAllowed = allowedRoutes.some(route => pathname.startsWith(route));

if (!isAllowed) {
  return NextResponse.redirect(new URL("/unauthorized", request.url));
}
```

**UI-level permissions** (hide/show buttons):

```typescript
import { hasRole } from '@/lib/auth';

function FileActions({ user }) {
  const canDelete = hasRole(user.role, ["SUPER_ADMIN", "TENANT_ADMIN"]);
  
  return (
    <div>
      <button>View</button>
      {canDelete && <button>Delete</button>}
    </div>
  );
}
```

⚠️ **Note**: UI permissions are just for better UX, backend must still check permissions on every API!

---

## 3. 🔄 Flow Diagrams

### 3.1. Login Flow

```
┌─────────┐
│  User   │
│ enters  │
│ email/  │
│password │
└────┬────┘
     │
     │ 1. Submit form
     ▼
┌─────────────────────────────┐
│  LoginPage                  │
│  (app/(auth)/login)         │
│                             │
│  → useAuth().signIn()       │
└────────────┬────────────────┘
             │
             │ 2. POST /api/auth/login
             ▼
┌─────────────────────────────┐
│  BFF Proxy                  │
│  (app/api/_lib/proxy.ts)    │
│                             │
│  → Validate CSRF            │
│  → Forward to backend       │
└────────────┬────────────────┘
             │
             │ 3. POST http://backend:8080/api/auth/login
             ▼
┌─────────────────────────────┐
│  Backend API                │
│                             │
│  → Verify email/password    │
│  → Generate JWT tokens      │
│  → Set-Cookie: accessToken  │
│  → Set-Cookie: refreshToken │
└────────────┬────────────────┘
             │
             │ 4. Response with Set-Cookie headers
             ▼
┌─────────────────────────────┐
│  BFF Proxy                  │
│                             │
│  → Forward Set-Cookie       │
└────────────┬────────────────┘
             │
             │ 5. Browser saves cookies automatically
             ▼
┌─────────────────────────────┐
│  AuthContext                │
│                             │
│  → mutate() (fetch /api/auth/me) │
│  → router.push("/files")    │
└────────────┬────────────────┘
             │
             │ 6. Redirect to /files
             ▼
┌─────────────────────────────┐
│  Middleware                 │
│  (src/middleware.ts)        │
│                             │
│  → Get accessToken from cookie│
│  → Verify JWT               │
│  → Check role permissions   │
│  → ✅ Allow access          │
└────────────┬────────────────┘
             │
             ▼
       ┌─────────┐
       │  /files │
       │  page   │
       └─────────┘
```

---

### 3.2. Protected Page Access Flow

```
User accesses /users
     │
     ▼
┌─────────────────────────────┐
│  Middleware checks          │
└────────────┬────────────────┘
             │
             ├─ No accessToken? ────────────────────────┐
             │                                          │
             │                                          ▼
             │                                    Redirect → /login
             │
             ├─ Invalid token? ─────────────────────────┐
             │  (verify JWT fail)                       │
             │                                          ▼
             │                                    Redirect → /login
             │                                    (+ clear cookies)
             │
             ├─ User on auth page? ─────────────────────┐
             │  (already logged in but accessing /login)│
             │                                          ▼
             │                                    Redirect → /files
             │
             ├─ No access to route? ────────────────────┐
             │  (role not in ROLE_ROUTE_MAP)            │
             │                                          ▼
             │                                    Redirect → /unauthorized
             │
             ▼
        ✅ Allow access
             │
             ▼
       Render page
```

---

### 3.3. Auto Refresh Token Flow

```
User calls API (e.g., GET /api/files)
     │
     ▼
┌─────────────────────────────┐
│  apiClient (axios)          │
│  → withCredentials: true    │
│  → Cookie sent automatically│
└────────────┬────────────────┘
             │
             ▼
┌─────────────────────────────┐
│  BFF Proxy                  │
│  → Get accessToken from cookie│
│  → Set Authorization header │
└────────────┬────────────────┘
             │
             ▼
        Backend API
             │
             ├─ Access token expired ───────────────────┐
             │                                          │
             │                                          ▼
             │                                    Return 401
             │                                          │
             ▼                                          │
        Return 200 OK                                   │
             │                                          │
             ▼                                          ▼
        Render data                         ┌─────────────────────────────┐
                                            │  apiClient Interceptor      │
                                            │                             │
                                            │  → Detect 401               │
                                            │  → Call POST /api/auth/refresh│
                                            └────────────┬────────────────┘
                                                         │
                                                         ▼
                                            ┌─────────────────────────────┐
                                            │  Backend refresh endpoint   │
                                            │                             │
                                            │  → Verify refreshToken      │
                                            │  → Generate new accessToken │
                                            │  → Set-Cookie with new token│
                                            └────────────┬────────────────┘
                                                         │
                                                         ├─ Refresh token also expired? ─────┐
                                                         │                                    │
                                                         │                                    ▼
                                                         │                              Force logout
                                                         │                   (dispatch "auth:logout" event)
                                                         │
                                                         ▼
                                            ┌─────────────────────────────┐
                                            │  apiClient Interceptor      │
                                            │                             │
                                            │  → Retry original request   │
                                            │    with new accessToken     │
                                            └────────────┬────────────────┘
                                                         │
                                                         ▼
                                                   ✅ Success
```

---

### 3.4. Forgot Password → Reset Password Flow

```
1. User enters email at /forgot-password
     │
     ▼
   POST /api/auth/forgot-password
     │
     ▼
   Backend sends email containing reset link
   (e.g., /reset-password?token=abc123)
     │
     ▼
2. User clicks link in email
     │
     ▼
   /reset-password?token=abc123
     │
     ▼
3. User enters new password
     │
     ▼
   POST /api/auth/reset-password
   { token: "abc123", password: "newpass" }
     │
     ▼
   Backend verifies token → updates password
     │
     ▼
   Redirect → /login?verified=true
```

---

## 4. 🎯 Important Points to Remember

### ✅ DOs

1. **Always use `useAuth()`** to get user info, don't fetch API `/me` manually
2. **Tokens are sent automatically via cookies**, no need to manually attach `Authorization` header
3. **UI permissions** using `hasRole()`, but backend must still verify
4. **Middleware auto-redirects**, no need to check auth in each page
5. **CSRF token is sent automatically** by `apiClient`, no need to worry

### ❌ DON'Ts

1. **Don't store tokens in localStorage** (vulnerable to XSS)
2. **Don't hardcode BACKEND_URL** on client, always call through BFF (`/api/*`)
3. **Don't skip middleware** for private routes
4. **Don't log tokens** to console (security risk)
5. **Don't trust client-side checks**, backend must validate again

---

## 5. 🚀 Guide for New Developers

### Step 1: Set up environment

```bash
# Clone project
git clone <repo-url>
cd frontend

# Install dependencies
npm install

# Create .env.local from template
cp .env.local.example .env.local

# Edit environment variables
# BACKEND_URL=http://localhost:8080
# JWT_SECRET=your-secret-here
```

### Step 2: Understand the login flow

1. Open file `src/app/(auth)/login/page.tsx` → see how form is submitted
2. Open file `src/contexts/auth-context.tsx` → see `signIn()` function
3. Open file `src/app/api/_lib/proxy.ts` → see how requests are forwarded to backend
4. Open file `src/middleware.ts` → see how auth is checked

### Step 3: Try debugging a request

1. Set breakpoint at `signIn()` in `auth-context.tsx`
2. Login with email/password
3. Check Network tab → see `accessToken` cookie being set
4. Refresh page → middleware auto-verifies token and allows access to `/files`

### Step 4: Add a new page (e.g., `/settings`)

```typescript
// 1. Create page: src/app/(dashboard)/settings/page.tsx
export default function SettingsPage() {
  const { user } = useAuth();
  return <div>Settings for {user?.fullName}</div>;
}

// 2. Update ROLE_ROUTE_MAP in middleware.ts
const ROLE_ROUTE_MAP = {
  SUPER_ADMIN: [..., "/settings"],
  // Add /settings to roles that should have access
};

// 3. Done! Middleware automatically protects this route
```

---

## 6. 📞 Frequently Asked Questions (FAQ)

### Q1: Why don't I see `Authorization` header when calling API from client?

**A**: Token is sent via **httpOnly cookie**, not header. BFF proxy extracts it from cookie and attaches it to header when forwarding to backend.

### Q2: How do I know if user is logged in?

**A**: Use `useAuth()` hook:

```typescript
const { user, isLoading } = useAuth();

if (isLoading) return <Spinner />;
if (!user) return <div>You are not logged in</div>;
return <div>Hello {user.fullName}</div>;
```

### Q3: When does auto refresh token run?

**A**: When encountering a 401 response, `apiClient` interceptor automatically calls `/api/auth/refresh` and retries the original request.

### Q4: How do I add a new role?

**A**:

1. Add to `src/lib/constants.ts`:
   ```typescript
   export const ROLES = {
     ...existing,
     MY_NEW_ROLE: "MY_NEW_ROLE",
   };
   ```
2. Update `ROLE_ROUTE_MAP` in `middleware.ts`

### Q5: How does CSRF token work?

**A**:

- BFF generates random token → sets it in cookie (readable by JS)
- Client reads cookie → sends via header `X-CSRF-Token`
- Server compares cookie vs header → must match to accept request

---

## 7. 🔧 Troubleshooting

### Error "Invalid CSRF token"

**Cause**: Cookie `csrf-token` not set or header doesn't match.

**Solution**:

1. Clear cookies and refresh page
2. Check if `apiClient` sends header `X-CSRF-Token`
3. Check if BFF proxy calls `ensureCsrfToken()`

### Error "Unauthorized" when already logged in

**Cause**: Middleware role check doesn't match with route.

**Solution**:

1. Check `ROLE_ROUTE_MAP` in `middleware.ts`
2. Check current user role: `console.log(user.role)`
3. Add route to that role's array

### Token auto-logout after a few minutes

**Cause**: Access token expired and refresh token also expired.

**Solution**:

1. Check backend config: access token TTL, refresh token TTL
2. Ensure refresh endpoint works correctly
3. Check if `refreshToken` cookie is being set

---

## 🎓 Conclusion

This project uses **BFF (Backend For Frontend) architecture** with **JWT authentication** in httpOnly cookies, providing:

✅ **High security**: Tokens never touch JavaScript (prevents XSS)  
✅ **Good UX**: Auto refresh token, users aren't suddenly logged out  
✅ **Easy to maintain**: Middleware centrally handles auth, no repeated code in each page  
✅ **Clear permissions**: Role-based access control is easy to understand and extend  

**Next steps**:

1. Review the flow diagrams to understand the flow
2. Debug step by step to see how middleware → auth context → API client work together
3. Try adding a new page to get familiar with permissions
4. Read code comments in source code for deeper understanding
