import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { AchievementSummary } from '../models/achievement.model';

/** Acces a `/api/v1/me/achievements` — jamais reserve a un sous-ensemble de comptes, tout compte
 * authentifie y a acces, memes gains nuls. */
@Injectable({ providedIn: 'root' })
export class AchievementService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/me/achievements`;

  constructor(private readonly http: HttpClient) {}

  summary(): Observable<ApiResponse<AchievementSummary>> {
    return this.http.get<ApiResponse<AchievementSummary>>(this.baseUrl);
  }
}
