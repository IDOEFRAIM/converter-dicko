import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AchievementSummary } from '../models/achievement.model';
import { ApiResponse } from '../models/api-response.model';

/** `GET /api/v1/me/achievements` — ouvert a tout compte authentifie (meme endpoint que le mobile). */
@Injectable({ providedIn: 'root' })
export class AchievementService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/me/achievements`;

  summary(): Observable<ApiResponse<AchievementSummary>> {
    return this.http.get<ApiResponse<AchievementSummary>>(this.baseUrl);
  }
}
