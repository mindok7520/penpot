import { z } from 'zod';
import { openUISchema } from '../models/open-ui-options.schema.js';
import { createModal } from '../create-modal.js';

const openUIArgsSchema = z.tuple([
  z.string(),
  z.string(),
  z.enum(['dark', 'light']),
  openUISchema.optional(),
  z.boolean().optional(),
  z.boolean().optional(),
  z.boolean().optional(),
]);

export function openUIApi(...args: z.infer<typeof openUIArgsSchema>) {
  const [
    title,
    url,
    theme,
    options,
    allowDownloads,
    allowClipboardRead,
    allowClipboardWrite,
  ] = openUIArgsSchema.parse(args);

  return createModal(
    title,
    url,
    theme,
    options,
    allowDownloads,
    allowClipboardRead,
    allowClipboardWrite,
  );
}
