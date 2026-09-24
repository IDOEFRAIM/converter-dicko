import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { CreatePoolRequest, Pool, PoolParticipant } from '../models/pool.model';

/** `/api/v1/pools` — memes appels que `PoolApi` (mobile). La contribution passe par `OrderService.create` (poolId). */
@Injectable({ providedIn: 'root' })
export class PoolService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/pools`;

  create(request: CreatePoolRequest): Observable<ApiResponse<Pool>> {
    return this.http.post<ApiResponse<Pool>>(this.baseUrl, request);
  }

  get(id: string): Observable<ApiResponse<Pool>> {
    return this.http.get<ApiResponse<Pool>>(`${this.baseUrl}/${id}`);
  }

  getByCode(code: string): Observable<ApiResponse<Pool>> {
    return this.http.get<ApiResponse<Pool>>(`${this.baseUrl}/by-code/${encodeURIComponent(code)}`);
  }

  participants(id: string): Observable<ApiResponse<PoolParticipant[]>> {
    return this.http.get<ApiResponse<PoolParticipant[]>>(`${this.baseUrl}/${id}/participants`);
  }

  mine(page = 0, size = 20): Observable<ApiResponse<PageResponse<Pool>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<Pool>>>(`${this.baseUrl}/mine`, { params });
  }

  join(id: string): Observable<ApiResponse<Pool>> {
    return this.http.post<ApiResponse<Pool>>(`${this.baseUrl}/${id}/join`, {});
  }

  cancel(id: string): Observable<ApiResponse<Pool>> {
    return this.http.post<ApiResponse<Pool>>(`${this.baseUrl}/${id}/cancel`, {});
  }
}
