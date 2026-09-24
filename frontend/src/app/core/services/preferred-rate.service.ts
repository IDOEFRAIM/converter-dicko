import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { CreatePreferredRateRequest, PreferredRateRequest } from '../models/preferred-rate.model';

@Injectable({ providedIn: 'root' })
export class PreferredRateService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/preferred-rates`;

  constructor(private readonly http: HttpClient) {}

  create(request: CreatePreferredRateRequest): Observable<ApiResponse<PreferredRateRequest>> {
    return this.http.post<ApiResponse<PreferredRateRequest>>(this.baseUrl, request);
  }

  list(page = 0, size = 20): Observable<ApiResponse<PageResponse<PreferredRateRequest>>> {
    return this.http.get<ApiResponse<PageResponse<PreferredRateRequest>>>(this.baseUrl, {
      params: { page, size },
    });
  }

  get(id: string): Observable<ApiResponse<PreferredRateRequest>> {
    return this.http.get<ApiResponse<PreferredRateRequest>>(`${this.baseUrl}/${id}`);
  }

  cancel(id: string): Observable<ApiResponse<PreferredRateRequest>> {
    return this.http.post<ApiResponse<PreferredRateRequest>>(`${this.baseUrl}/${id}/cancel`, {});
  }
}
