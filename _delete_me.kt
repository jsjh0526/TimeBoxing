            // ── Level 1: 제목(작게) + 시간범위 + DurationChip (< 36dp) ──
            cardH < 36.dp -> {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = task.title,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(color = TextPrimary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timeText,
                        style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, lineHeight = 12.sp, fontFamily = FontFamily.Monospace),
                        maxLines = 1
                    )
                    DurationChip(
                        durationMinutes = schedule.durationMinutes,
                        expanded = durationExpanded,
                        readOnly = readOnly || isDragging,
                        options = durationOptions,
                        onExpandedChange = { durationExpanded = it },
                        onSelect = onChangeDuration,
                        compact = true
                    )
                }
            }