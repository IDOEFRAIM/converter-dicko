import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { KycDocumentType, KycSubmission } from '../models/kyc.model';

/** Verification d'identite en libre-service — memes endpoints que `KycApi` (mobile). */
@Injectable({ providedIn: 'root' })
export class KycService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/kyc/submissions`;

  mySubmission(): Observable<ApiResponse<KycSubmission | null>> {
    return this.http.get<ApiResponse<KycSubmission | null>>(`${this.baseUrl}/me`);
  }

  submit(
    documentType: KycDocumentType,
    front: File,
    back: File | null,
    selfie: File,
  ): Observable<ApiResponse<KycSubmission>> {
    const form = new FormData();
    form.append('documentType', documentType);
    form.append('front', front);
    if (back) {
      form.append('back', back);
    }
    form.append('selfie', selfie);
    return this.http.post<ApiResponse<KycSubmission>>(this.baseUrl, form);
  }
}
