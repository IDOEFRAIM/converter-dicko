import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { InstallAppBannerComponent } from './shared/components/install-app-banner/install-app-banner.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, InstallAppBannerComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
