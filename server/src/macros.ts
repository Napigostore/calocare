/** Personalized macro target calculator based on user goal and metrics. */

export interface UserMetrics {
  weightKg: number;
  heightCm: number;
  age: number;
  male: boolean;
  activityLevel: number; // 1.2 (sedentary) to 1.9 (very active)
  goal: "lose" | "gain" | "maintain" | "gym" | "run";
  weeklyRateKg?: number; // kg to lose/gain per week
  dietRestriction?: "keto" | "none"; // for keto, carbs are lower
}

export interface MacroTargets {
  proteinG: number;
  carbsG: number;
  fatG: number;
  kcal: number;
  explanation: string; // why this split
}

/** Mifflin-St Jeor formula for BMR. Same as mobile apps. */
function bmr(metrics: UserMetrics): number {
  const { weightKg, heightCm, age, male } = metrics;
  const ageInt = Math.min(100, Math.max(10, age));
  return 10 * weightKg + 6.25 * heightCm - 5 * ageInt + (male ? 5 : -161);
}

/** Total daily energy expenditure = BMR * activity level. */
function tdee(metrics: UserMetrics): number {
  return bmr(metrics) * metrics.activityLevel;
}

/**
 * Calculate personalized macro targets for the user's goal.
 * Returns protein, carbs, fat in grams and total kcal.
 */
export function macroTargets(metrics: UserMetrics): MacroTargets {
  const baseTdee = tdee(metrics);
  const weeklyRate = metrics.weeklyRateKg ?? 0.5; // kg per week
  const perDay = (weeklyRate * 7700) / 7; // kcal for target rate

  let kcal = baseTdee;
  let explanation = "";

  switch (metrics.goal) {
    case "lose":
      kcal = baseTdee - Math.min(perDay, 1000); // max 1000 kcal deficit
      explanation = `Deficit of ${Math.round(baseTdee - kcal)} kcal/day to lose ~${weeklyRate} kg/week`;
      break;
    case "gain":
      kcal = baseTdee + Math.min(perDay, 700);
      explanation = `Surplus of ${Math.round(kcal - baseTdee)} kcal/day to gain ~${weeklyRate} kg/week`;
      break;
    case "gym":
      kcal = baseTdee + 250;
      explanation = "Extra 250 kcal/day for muscle building";
      break;
    case "run":
      explanation = "High carbs for endurance training";
      break;
    default:
      explanation = "Maintenance calories";
  }

  // Crash diet prevention
  kcal = Math.max(kcal, metrics.male ? 1500 : 1200);

  const split = macroSplit(metrics.goal, metrics.dietRestriction);
  const proteinKcal = kcal * split.protein;
  const carbsKcal = kcal * split.carbs;
  const fatKcal = kcal * split.fat;

  let proteinG = Math.round(proteinKcal / 4);
  let carbsG = Math.round(carbsKcal / 4);
  let fatG = Math.round(fatKcal / 9);

  // For gym goal, use 2g protein per kg body weight (more aggressive)
  if (metrics.goal === "gym") {
    proteinG = Math.round(2 * metrics.weightKg);
    fatG = Math.round((kcal * 0.25) / 9);
    carbsG = Math.max(0, Math.round((kcal - proteinG * 4 - fatG * 9) / 4));
  }

  return {
    proteinG,
    carbsG,
    fatG,
    kcal: Math.round(kcal),
    explanation,
  };
}

/**
 * Macro split (% of calories) for each goal.
 * Default assumes some carb tolerance.
 */
function macroSplit(goal: string, diet?: string): { protein: number; carbs: number; fat: number } {
  if (diet === "keto") {
    return { protein: 0.25, carbs: 0.08, fat: 0.67 };
  }
  switch (goal) {
    case "lose":
      return { protein: 0.3, carbs: 0.4, fat: 0.3 };
    case "gain":
      return { protein: 0.2, carbs: 0.55, fat: 0.25 };
    case "run":
      return { protein: 0.18, carbs: 0.6, fat: 0.22 };
    case "gym":
      return { protein: 0.3, carbs: 0.45, fat: 0.25 };
    default:
      return { protein: 0.2, carbs: 0.5, fat: 0.3 }; // maintain
  }
}
