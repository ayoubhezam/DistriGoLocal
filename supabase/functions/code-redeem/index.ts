// POST code-redeem: « Saisir un code » in Compte, see _shared/service.ts, renewWithCode.
import { serve } from "../_shared/http.ts";
import { renewWithCode } from "../_shared/service.ts";

serve(renewWithCode);
