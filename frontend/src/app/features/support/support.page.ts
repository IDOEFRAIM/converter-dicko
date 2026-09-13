import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SupportService } from '../../core/services/support.service';
import { extractErrorMessage } from '../../core/services/api-error.util';
import { SupportMessage } from '../../core/models/support.model';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

/**
 * Messagerie SAV du client (retour client : "les utilisateurs doivent pouvoir faire des
 * reclamations") — un seul fil continu, jamais un ticket par reclamation. Rafraichi
 * periodiquement (pas de WebSocket dans ce MVP, meme discipline que le reste du paiement/reglement
 * "entierement manuel") pour voir arriver la reponse de l'admin sans recharger la page.
 */
@Component({
  selector: 'app-support-page',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './support.page.html',
  styleUrl: './support.page.scss',
})
export class SupportPage implements OnInit {
  private readonly supportService = inject(SupportService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly sending = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly messages = signal<SupportMessage[]>([]);

  readonly form = new FormGroup({
    body: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(2000)] }),
  });

  ngOnInit(): void {
    this.load(true);
    const intervalId = setInterval(() => this.load(false), 10_000);
    this.destroyRef.onDestroy(() => clearInterval(intervalId));
  }

  private load(showSpinner: boolean): void {
    if (showSpinner) {
      this.loading.set(true);
    }
    this.supportService.myThread().subscribe({
      next: (response) => {
        this.messages.set(response.data.messages);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  send(): void {
    if (this.form.invalid || this.sending()) {
      this.form.markAllAsTouched();
      return;
    }
    const body = this.form.controls.body.value.trim();
    if (!body) {
      return;
    }
    this.sending.set(true);
    this.errorMessage.set(null);
    this.supportService.send({ body }).subscribe({
      next: (response) => {
        this.messages.update((current) => [...current, response.data]);
        this.form.reset({ body: '' });
        this.sending.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.sending.set(false);
      },
    });
  }
}
