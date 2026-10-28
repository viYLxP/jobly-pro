import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { TranslatePipe } from '../core/i18n/translate.pipe';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty-state empty-state--page">
      <h1>{{ 'notFound.title' | t }}</h1>
      <p>{{ 'notFound.text' | t }}</p>
      <a class="btn btn--primary" routerLink="/">{{ 'notFound.home' | t }}</a>
    </div>
  `,
})
export class NotFoundPage {}
