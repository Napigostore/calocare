/** Calculate eating habit scores for gamification. */

export interface HabitScore {
  diversity: number; // 0-100: how many different foods
  timing: number; // 0-100: meal consistency
  macroBalance: number; // 0-100: hitting macro targets
  overall: number; // 0-100: weighted average
  streak: number; // consecutive days hitting 70+
  nextBadge?: string; // badge name if eligible
}

export interface DayNutrition {
  date: string; // yyyy-MM-dd
  foods: string[]; // food names logged
  kcal: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
}

export interface UserGoal {
  kcal: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
}

/**
 * Calculate eating habit score based on a day's nutrition.
 * Scores reward consistency, variety, and hitting macro targets.
 */
export function computeHabitScore(
  today: DayNutrition,
  target: UserGoal,
  pastDays: DayNutrition[] = [],
): HabitScore {
  // 1. Diversity: how many unique foods logged (max 100 at 15+ foods)
  const uniqueFoods = new Set(today.foods).size;
  const diversity = Math.min(100, (uniqueFoods / 15) * 100);

  // 2. Timing: not applicable here (would need meal times), assume 100
  const timing = 100;

  // 3. Macro Balance: how close to target (±10% = full points)
  const kcalDiff = Math.abs(today.kcal - target.kcal) / target.kcal;
  const proteinDiff = Math.abs(today.protein_g - target.protein_g) / target.protein_g;
  const carbsDiff = Math.abs(today.carbs_g - target.carbs_g) / target.carbs_g;
  const fatDiff = Math.abs(today.fat_g - target.fat_g) / target.fat_g;

  const avgDiff = (kcalDiff + proteinDiff + carbsDiff + fatDiff) / 4;
  const macroBalance = Math.max(0, 100 - avgDiff * 100); // 100 if perfect, 0 if off by 2x

  // Overall: weighted average (macro balance matters most)
  const overall = diversity * 0.25 + timing * 0.25 + macroBalance * 0.5;

  // Streak: count consecutive "good" days (70+) from most recent backwards
  let streak = 0;
  for (const day of [...pastDays].reverse()) {
    const dayScore = computeHabitScore(day, target, []).overall;
    if (dayScore >= 70) streak++;
    else break;
  }
  if (overall >= 70) streak++;

  // Badges: unlock at milestones
  let nextBadge: string | undefined;
  if (streak === 7) nextBadge = "week_warrior"; // 7 days ≥70
  if (streak === 30) nextBadge = "month_master"; // 30 days ≥70
  if (diversity >= 15) nextBadge = "food_explorer"; // 15+ unique foods
  if (macroBalance >= 90) nextBadge = "macro_precision"; // 90+ macro score

  return {
    diversity: Math.round(diversity),
    timing,
    macroBalance: Math.round(macroBalance),
    overall: Math.round(overall),
    streak,
    nextBadge,
  };
}

export const badges = {
  week_warrior: {
    name: "🏆 Week Warrior",
    description: "7 days in a row with great nutrition",
    requirement: "7-day streak",
  },
  month_master: {
    name: "👑 Month Master",
    description: "30 days of consistent eating habits",
    requirement: "30-day streak",
  },
  food_explorer: {
    name: "🌍 Food Explorer",
    description: "Logged 15 different foods in one day",
    requirement: "15+ unique foods",
  },
  macro_precision: {
    name: "💯 Macro Precision",
    description: "Hit all macro targets perfectly",
    requirement: "90+ macro balance score",
  },
};
