import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { InboxNotification } from '../models/inbox-notification.model';

@Injectable({ providedIn: 'root' })
export class InboxNotificationService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/notifications`;

  constructor(private readonly http: HttpClient) {}

  list(page = 0, size = 20): Observable<ApiResponse<PageResponse<InboxNotification>>> {
    return this.http.get<ApiResponse<PageResponse<InboxNotification>>>(this.baseUrl, {
      params: { page, size },
    });
  }

  unreadCount(): Observable<ApiResponse<{ unreadCount: number }>> {
    return this.http.get<ApiResponse<{ unreadCount: number }>>(`${this.baseUrl}/unread-count`);
  }

  markRead(id: string): Observable<ApiResponse<InboxNotification>> {
    return this.http.post<ApiResponse<InboxNotification>>(`${this.baseUrl}/${id}/read`, {});
  }
}
