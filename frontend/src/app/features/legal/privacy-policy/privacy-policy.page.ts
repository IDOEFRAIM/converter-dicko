import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Location } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

/**
 * Politique de confidentialite reelle de YUAN PAY BF -- reflete uniquement les donnees
 * effectivement collectees par cette application (voir User/KycSubmission/Order/Supplier/Payment
 * backend), jamais un texte generique ou copie d'un autre service. Route publique (aucun guard) :
 * accessible avant meme la creation d'un compte, depuis la page d'inscription.
 */
@Component({
  selector: 'app-privacy-policy-page',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="privacy-page">
      <app-page-header title="Politique de confidentialite" subtitle="YUAN PAY BF - transferts XOF -> CNY">
        <button mat-button (click)="back()"><mat-icon>arrow_back</mat-icon> Retour</button>
      </app-page-header>

      <p class="privacy-page__updated">Derniere mise a jour : septembre 2026</p>

      <section>
        <h2>1. Qui sommes-nous</h2>
        <p>
          YUAN PAY BF est un service de transfert d'argent qui permet a ses utilisateurs, depuis le Burkina Faso,
          de convertir des francs CFA (XOF) en yuans (CNY) et de les faire parvenir a un beneficiaire en Chine
          (compte bancaire, Alipay ou WeChat Pay). Cette politique explique quelles informations nous collectons
          lorsque vous utilisez l'application, pourquoi, et comment elles sont protegees.
        </p>
      </section>

      <section>
        <h2>2. Informations que nous collectons</h2>
        <ul>
          <li><strong>Compte</strong> : votre numero de telephone (identifiant de connexion), votre nom et prenom,
            votre mot de passe (jamais conserve en clair, uniquement sous forme chiffree/haché) et, si vous
            utilisez "Continuer avec Google", votre adresse email.</li>
          <li><strong>Verification d'identite (KYC)</strong> : uniquement si vous effectuez un transfert
            au-dela du seuil qui la rend obligatoire -- une photo de votre piece d'identite (recto/verso) et un
            selfie, examines par notre equipe puis conserves pour justifier la verification effectuee.</li>
          <li><strong>Transferts</strong> : le detail de vos devis et ordres (montants XOF/CNY, taux, date,
            statut), le motif du transfert si vous le renseignez.</li>
          <li><strong>Beneficiaires</strong> : les coordonnees des personnes que vous enregistrez comme
            destinataires en Chine (nom, numero de compte bancaire chinois, ou code QR Alipay/WeChat Pay).</li>
          <li><strong>Paiement</strong> : les informations que vous declarez pour justifier un paiement
            (moyen de paiement, reference de transaction, nom et numero du payeur) ainsi que toute preuve de
            paiement que vous televersez (capture d'ecran ou photo de recu).</li>
          <li><strong>Support</strong> : le contenu des messages que vous envoyez a notre service client via la
            messagerie de l'application.</li>
        </ul>
        <p>
          Nous n'utilisons aucun cookie publicitaire ni traceur tiers (pas de Facebook, Google Ads, TikTok ou
          equivalent) : l'application ne collecte pas d'identifiant publicitaire et ne partage aucune donnee a des
          fins de publicite ciblee.
        </p>
      </section>

      <section>
        <h2>3. Pourquoi nous utilisons ces informations</h2>
        <ul>
          <li>Creer et securiser votre compte, vous authentifier.</li>
          <li>Traiter vos transferts : calculer le taux, creer l'ordre, verifier et regler le paiement, effectuer
            le reglement en Chine.</li>
          <li>Verifier votre identite lorsque la loi ou nos regles internes l'exigent (montants eleves), pour
            prevenir la fraude et le blanchiment.</li>
          <li>Repondre a vos demandes via la messagerie de support.</li>
          <li>Detecter et prevenir les usages frauduleux de la plateforme.</li>
        </ul>
      </section>

      <section>
        <h2>4. Avec qui ces informations sont partagees</h2>
        <p>
          Vos informations ne sont <strong>jamais vendues</strong> et ne sont jamais partagees a des fins
          publicitaires. Elles sont accessibles uniquement a notre equipe (administrateurs autorises), dans la
          seule mesure necessaire pour traiter votre transfert, verifier votre identite ou repondre a votre
          demande de support. Les coordonnees de votre beneficiaire en Chine ne servent qu'a effectuer le
          reglement de votre ordre, jamais a un autre usage.
        </p>
        <p>
          Nous pouvons divulguer des informations si la loi nous y oblige (requisition judiciaire ou
          administrative), ou pour proteger nos utilisateurs contre une fraude averee.
        </p>
      </section>

      <section>
        <h2>5. Comment nous protegeons vos informations</h2>
        <ul>
          <li>Votre mot de passe n'est jamais stocke en clair : seule son empreinte chiffree (BCrypt) est
            conservee.</li>
          <li>Les echanges entre l'application et nos serveurs sont chiffres (HTTPS/TLS).</li>
          <li>Votre session est protegee par un jeton (JWT) avec une duree de validite limitee.</li>
          <li>L'acces aux donnees sensibles (piece d'identite, preuves de paiement) est reserve aux
            administrateurs habilites, dans le cadre strict du traitement de votre dossier.</li>
        </ul>
      </section>

      <section>
        <h2>6. Combien de temps nous conservons vos informations</h2>
        <p>
          Nous conservons vos informations tant que votre compte existe, afin de pouvoir vous presenter
          l'historique de vos transferts. Vous pouvez a tout moment demander la suppression de votre compte et de
          vos donnees personnelles en nous contactant a l'adresse ci-dessous ; nous y donnons suite sous reserve
          des obligations legales de conservation qui pourraient s'appliquer (ex. lutte anti-blanchiment).
        </p>
      </section>

      <section>
        <h2>7. Vos droits</h2>
        <p>
          Vous pouvez a tout moment nous demander d'acceder a vos donnees personnelles, de les corriger, ou de les
          supprimer, en nous ecrivant a l'adresse ci-dessous.
        </p>
      </section>

      <section>
        <h2>8. Modifications de cette politique</h2>
        <p>
          Nous pouvons mettre a jour cette politique si nos services evoluent. La version en vigueur est toujours
          celle publiee sur cette page.
        </p>
      </section>

      <section>
        <h2>9. Nous contacter</h2>
        <p>
          Pour toute question concernant cette politique ou vos donnees personnelles, ecrivez-nous a
          <a href="mailto:stevedicko98@gmail.com">stevedicko98&#64;gmail.com</a>.
        </p>
      </section>
    </div>
  `,
  styles: [
    `
      .privacy-page {
        max-width: 720px;
        margin: 0 auto;
        padding: 1.5rem 1.25rem 3rem;
      }
      .privacy-page__updated {
        color: var(--mat-sys-on-surface-variant, #616161);
        font-size: 0.85rem;
        margin: 0 0 1.5rem;
      }
      section {
        margin-bottom: 1.75rem;
      }
      h2 {
        font-size: 1.05rem;
        color: var(--brand-navy, #1b325e);
        margin: 0 0 0.5rem;
      }
      p,
      li {
        line-height: 1.6;
        color: var(--mat-sys-on-surface, #1a1c1e);
      }
      ul {
        margin: 0;
        padding-left: 1.25rem;
      }
      li {
        margin-bottom: 0.4rem;
      }
      a {
        color: var(--brand-navy, #1b325e);
      }
    `,
  ],
})
export class PrivacyPolicyPage {
  private readonly location = inject(Location);

  back(): void {
    this.location.back();
  }
}
