import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import {
  SendSupportMessageRequest,
  SupportMessage,
  SupportThread,
  SupportThreadSummary,
} from '../models/support.model';

/** Cote client : `/api/v1/support/**` (mon fil uniquement). */
@Injectable({ providedIn: 'root' })
export class SupportService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/support`;

  constructor(private readonly http: HttpClient) {}

  myThread(): Observable<ApiResponse<SupportThread>> {
    return this.http.get<ApiResponse<SupportThread>>(`${this.baseUrl}/thread`);
  }

  send(request: SendSupportMessageRequest): Observable<ApiResponse<SupportMessage>> {
    return this.http.post<ApiResponse<SupportMessage>>(`${this.baseUrl}/messages`, request);
  }
}

/** Cote admin : `/api/admin/support/**` (boite de reception complete). */
@Injectable({ providedIn: 'root' })
export class AdminSupportService {
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/support`;

  constructor(private readonly http: HttpClient) {}

  listThreads(page = 0, size = 20): Observable<ApiResponse<PageResponse<SupportThreadSummary>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<SupportThreadSummary>>>(`${this.baseUrl}/threads`, { params });
  }

  getThread(userId: string): Observable<ApiResponse<SupportThread>> {
    return this.http.get<ApiResponse<SupportThread>>(`${this.baseUrl}/threads/${userId}/messages`);
  }

  reply(userId: string, request: SendSupportMessageRequest): Observable<ApiResponse<SupportMessage>> {
    return this.http.post<ApiResponse<SupportMessage>>(`${this.baseUrl}/threads/${userId}/messages`, request);
  }
}
