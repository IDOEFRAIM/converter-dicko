import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { PublishRateRequest, RateSource } from '../models/rate.model';

@Injectable({ providedIn: 'root' })
export class AdminRateService {
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/rates`;

  constructor(private readonly http: HttpClient) {}

  publish(request: PublishRateRequest): Observable<ApiResponse<RateSource>> {
    return this.http.post<ApiResponse<RateSource>>(this.baseUrl, request);
  }

  history(page = 0, size = 20): Observable<ApiResponse<PageResponse<RateSource>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<RateSource>>>(this.baseUrl, { params });
  }
}
