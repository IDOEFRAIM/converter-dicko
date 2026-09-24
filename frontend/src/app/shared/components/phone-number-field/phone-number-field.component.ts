import { ChangeDetectionStrategy, Component, DestroyRef, Input, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

interface CountryCode {
  flag: string;
  dialCode: string;
  name: string;
}

/** Volontairement limite aux pays les plus pertinents pour cette plateforme (Burkina Faso en
 * premier/par defaut), pas un annuaire mondial complet -- garde le selecteur rapide a parcourir. */
const COUNTRY_CODES: CountryCode[] = [
  { flag: '🇧🇫', dialCode: '+226', name: 'Burkina Faso' },
  { flag: '🇨🇮', dialCode: '+225', name: "Cote d'Ivoire" },
  { flag: '🇲🇱', dialCode: '+223', name: 'Mali' },
  { flag: '🇸🇳', dialCode: '+221', name: 'Senegal' },
  { flag: '🇳🇪', dialCode: '+227', name: 'Niger' },
  { flag: '🇹🇬', dialCode: '+228', name: 'Togo' },
  { flag: '🇧🇯', dialCode: '+229', name: 'Benin' },
  { flag: '🇬🇳', dialCode: '+224', name: 'Guinee' },
  { flag: '🇬🇭', dialCode: '+233', name: 'Ghana' },
  { flag: '🇳🇬', dialCode: '+234', name: 'Nigeria' },
  { flag: '🇨🇳', dialCode: '+86', name: 'Chine' },
  { flag: '🇫🇷', dialCode: '+33', name: 'France' },
];

/**
 * Champ telephone compose d'un selecteur d'indicatif pays + d'un numero local : l'utilisateur ne
 * tape jamais lui-meme le "+" ni l'indicatif (retour beta-testeur sept. 2026 : "le fait de devoir
 * ajouter le +... est fatiguant, l'utilisateur rentre juste le code pays plus son numero").
 *
 * <p>Recoit un {@link FormControl} deja present dans le formulaire parent (`control`) et ecrit
 * dedans le numero complet au format E.164 (ex. "+22670026919") a chaque frappe -- exactement ce
 * qu'attendent la validation existante du formulaire parent et le backend. N'implemente pas
 * `ControlValueAccessor` : ce composant EST le champ (comme les autres composants "field" de ce
 * dossier), pas une valeur enveloppee dans `formControlName`.
 */
@Component({
  selector: 'app-phone-number-field',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="phone-number-field">
      <mat-form-field appearance="outline" class="phone-number-field__dial-code">
        <mat-label>Indicatif</mat-label>
        <mat-select [(value)]="selectedDialCode" (selectionChange)="syncFullNumber()">
          @for (country of countryCodes; track country.dialCode) {
            <mat-option [value]="country.dialCode">{{ country.flag }} {{ country.dialCode }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="phone-number-field__local-number">
        <mat-label>{{ labelText }}</mat-label>
        <input
          matInput
          [(ngModel)]="localDigits"
          (ngModelChange)="syncFullNumber()"
          [autocomplete]="autocomplete"
          placeholder="70026919"
        />
      </mat-form-field>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
      }
      .phone-number-field {
        display: flex;
        gap: 0.5rem;
        align-items: flex-start;
      }
      .phone-number-field__dial-code {
        width: 110px;
        flex-shrink: 0;
      }
      .phone-number-field__local-number {
        flex: 1;
      }
    `,
  ],
})
export class PhoneNumberFieldComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) control!: FormControl<string>;
  @Input() labelText = 'Numero de telephone';
  @Input() autocomplete = 'tel';

  readonly countryCodes = COUNTRY_CODES;
  selectedDialCode = COUNTRY_CODES[0].dialCode;
  localDigits = '';

  ngOnInit(): void {
    const existing = this.control.value?.trim() ?? '';
    // Si le controle porte deja une valeur (ex. re-ouverture d'un formulaire), on retrouve
    // l'indicatif correspondant plutot que d'ecraser silencieusement un numero deja saisi.
    const match = this.countryCodes.find((country) => existing.startsWith(country.dialCode));
    if (match) {
      this.selectedDialCode = match.dialCode;
      this.localDigits = existing.substring(match.dialCode.length);
    }
    // Si le FormControl parent est reinitialise programmatique (ex. formulaire vide apres
    // soumission), on garde ce composant synchronise plutot que de figer un ancien affichage.
    this.control.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((value) => {
      if (value === this.fullNumber()) {
        return;
      }
      if (!value) {
        this.localDigits = '';
      }
    });
  }

  syncFullNumber(): void {
    this.control.setValue(this.fullNumber());
  }

  private fullNumber(): string {
    const digits = this.localDigits.trim();
    return digits.length === 0 ? '' : `${this.selectedDialCode}${digits}`;
  }
}
