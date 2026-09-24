import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { CreatePoolRequest, Pool, PoolParticipant } from '../models/pool.model';

/** Acces a `/api/v1/pools` — creation, invitation par code court, consultation (ouverte a tout
 * compte authentifie, meme avant de rejoindre), participants, "mes Ruees", annulation. */
@Injectable({ providedIn: 'root' })
export class PoolService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/pools`;

  constructor(private readonly http: HttpClient) {}

  create(request: CreatePoolRequest): Observable<ApiResponse<Pool>> {
    return this.http.post<ApiResponse<Pool>>(this.baseUrl, request);
  }

  mine(page = 0, size = 50): Observable<ApiResponse<PageResponse<Pool>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<Pool>>>(`${this.baseUrl}/mine`, { params });
  }

  get(id: string): Observable<ApiResponse<Pool>> {
    return this.http.get<ApiResponse<Pool>>(`${this.baseUrl}/${id}`);
  }

  getByCode(code: string): Observable<ApiResponse<Pool>> {
    return this.http.get<ApiResponse<Pool>>(`${this.baseUrl}/by-code/${code}`);
  }

  participants(id: string): Observable<ApiResponse<PoolParticipant[]>> {
    return this.http.get<ApiResponse<PoolParticipant[]>>(`${this.baseUrl}/${id}/participants`);
  }

  join(id: string): Observable<ApiResponse<Pool>> {
    return this.http.post<ApiResponse<Pool>>(`${this.baseUrl}/${id}/join`, {});
  }

  cancel(id: string): Observable<ApiResponse<Pool>> {
    return this.http.post<ApiResponse<Pool>>(`${this.baseUrl}/${id}/cancel`, {});
  }
}
