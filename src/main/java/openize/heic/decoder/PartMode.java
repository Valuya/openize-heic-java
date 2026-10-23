/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package openize.heic.decoder;


enum PartMode
{
    part2Nx2N, /*  = 0 */
    part2NxN, /*  = 1 */
    partNx2N, /*  = 2 */
    partNxN, /*  = 3 */
    part2NxnU, /*  = 4 */
    part2NxnD, /*  = 5 */
    partNLx2N, /*  = 6 */
    partNRx2N, /*  = 7 */
}

