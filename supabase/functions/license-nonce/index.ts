// POST license-nonce: see _shared/service.ts, createNonce.
import { serve } from "../_shared/http.ts";
import { createNonce } from "../_shared/service.ts";

serve(createNonce);
