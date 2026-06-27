import axios from 'axios';

declare module 'axios' {
  export interface InternalAxiosRequestConfig {
    _retry?: boolean;
  }
}

let accessToken: string | null = null;

export const getAccessToken = () => accessToken;

export const setAccessToken = (token: string | null) => {
  accessToken = token;
};

const client = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL ?? 'https://api.booktown.shop'}/api/v1`,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request Interceptor: Attach access token to authorization header if available
client.interceptors.request.use(
  (config) => {
    const token = getAccessToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

let isReissuing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else if (token) {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

// Response Interceptor: Handle global errors (e.g. token expired, server error)
client.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {
    const originalRequest = error.config;

    // Check if originalRequest is missing config
    if (!originalRequest) {
      return Promise.reject(error);
    }

    const isAuthRequest = originalRequest.url?.includes('/auth/login') ||
                          originalRequest.url?.includes('/auth/reissue') ||
                          originalRequest.url?.includes('/auth/signup');

    const isMockToken = getAccessToken()?.startsWith('mock_jwt_access_token');

    // Check if error is 401 Unauthorized (token expired) and not retried yet and not an auth request, and not a mock token
    if (error.response?.status === 401 && !originalRequest._retry && !isAuthRequest && !isMockToken) {
      if (isReissuing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({
            resolve: (token: string) => {
              originalRequest._retry = true;
              originalRequest.headers.Authorization = `Bearer ${token}`;
              resolve(client(originalRequest));
            },
            reject: (err: unknown) => {
              reject(err);
            },
          });
        });
      }

      originalRequest._retry = true;
      isReissuing = true;

      return new Promise((resolve, reject) => {
        axios
          .post(
            `${import.meta.env.VITE_API_BASE_URL ?? 'https://api.booktown.shop'}/api/v1/auth/reissue`,
            {},
            { withCredentials: true }
          )
          .then(({ data }) => {
            const newAccessToken = data.data.accessToken;
            setAccessToken(newAccessToken);
            originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
            processQueue(null, newAccessToken);
            resolve(client(originalRequest));
          })
          .catch((reissueError) => {
            processQueue(reissueError, null);
            setAccessToken(null);
            if (typeof window !== 'undefined') {
              window.location.href = '/login';
            }
            reject(reissueError);
          })
          .finally(() => {
            isReissuing = false;
          });
      });
    }


    return Promise.reject(error);
  }
);

export default client;

