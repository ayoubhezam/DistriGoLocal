// POST business-profile: « Informations de l'entreprise » in Compte, see _shared/service.ts, businessProfile.
import { serve } from "../_shared/http.ts";
import { businessProfile } from "../_shared/service.ts";

serve(businessProfile);
