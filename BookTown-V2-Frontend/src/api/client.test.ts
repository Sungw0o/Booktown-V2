import { describe, it, expect, vi, beforeEach, beforeAll, afterEach } from 'vitest';
import axios, { AxiosError } from 'axios';
import type { InternalAxiosRequestConfig, AxiosResponse } from 'axios';
import client, { getAccessToken, setAccessToken } from './client';

describe('Axios Client Test', () => {
  let originalAdapter: typeof client.defaults.adapter;

  beforeAll(() => {
    originalAdapter = client.defaults.adapter;
  });

  beforeEach(() => {
    setAccessToken(null);
    vi.restoreAllMocks();
  });

  afterEach(() => {
    client.defaults.adapter = originalAdapter;
  });

  it('should be configured with correct default prefix', () => {
    expect(client.defaults.baseURL).toContain('/api/v1');
  });

  it('should have application/json Content-Type header by default', () => {
    expect(client.defaults.headers['Content-Type']).toBe('application/json');
  });

  it('should set and get access token in memory', () => {
    setAccessToken('test-token');
    expect(getAccessToken()).toBe('test-token');
  });

  it('should attach access token to request header when set', async () => {
    setAccessToken('my-secret-token');

    let requestConfig: InternalAxiosRequestConfig | null = null;
    client.defaults.adapter = async (config) => {
      requestConfig = config;
      return { data: 'ok', status: 200, statusText: 'OK', headers: {}, config } as unknown as AxiosResponse;
    };

    await client.get('/test-endpoint');

    expect(requestConfig).not.toBeNull();
    expect(requestConfig!.headers.Authorization).toBe('Bearer my-secret-token');
  });

  it('should queue multiple concurrent 401 requests and retry them after successful reissue', async () => {
    const mockReissue = vi.spyOn(axios, 'post').mockResolvedValue({
      data: {
        data: {
          accessToken: 'new-accessToken-123'
        }
      }
    });

    let firstAttemptCount = 0;
    let retryAttemptCount = 0;

    client.defaults.adapter = async (config) => {
      if (!config._retry) {
        firstAttemptCount++;
        const error = new AxiosError(
          'Unauthorized',
          '401',
          config,
          null,
          { status: 401, data: {}, headers: {}, config } as unknown as AxiosResponse
        );
        throw error;
      } else {
        retryAttemptCount++;
        return { data: 'success', status: 200, statusText: 'OK', headers: {}, config } as unknown as AxiosResponse;
      }
    };

    const [res1, res2] = await Promise.all([
      client.get('/endpoint-1'),
      client.get('/endpoint-2')
    ]);

    expect(mockReissue).toHaveBeenCalledTimes(1);
    expect(mockReissue).toHaveBeenCalledWith(
      expect.stringContaining('/auth/reissue'),
      {},
      expect.objectContaining({ withCredentials: true })
    );

    expect(firstAttemptCount).toBe(2);
    expect(retryAttemptCount).toBe(2);
    expect(getAccessToken()).toBe('new-accessToken-123');
    expect(res1.data).toBe('success');
    expect(res2.data).toBe('success');
    expect(res1.config.headers.Authorization).toBe('Bearer new-accessToken-123');
    expect(res2.config.headers.Authorization).toBe('Bearer new-accessToken-123');
  });
});



