import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { PublicSettings } from '../models/settings.model';

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly baseUrl = `${environment.apiBaseUrl}/settings`;
  private cached$?: Observable<ApiResponse<PublicSettings>>;

  constructor(private readonly http: HttpClient) {}

  /** Bornes/parametres publics — mis en cache pour la duree de la session (rarement modifies). */
  getPublicSettings(): Observable<ApiResponse<PublicSettings>> {
    if (!this.cached$) {
      this.cached$ = this.http
        .get<ApiResponse<PublicSettings>>(`${this.baseUrl}/public`)
        .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.cached$;
  }
}
