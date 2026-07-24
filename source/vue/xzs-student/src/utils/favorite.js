const FAVORITE_KEY = 'xzs_favorite_papers'
const FAVORITE_LIMIT = 100

function safeParse (str) {
  try {
    return JSON.parse(str)
  } catch (e) {
    return []
  }
}

function safeStringify (obj) {
  try {
    return JSON.stringify(obj)
  } catch (e) {
    return '[]'
  }
}

export function getFavorites () {
  try {
    const data = window.localStorage.getItem(FAVORITE_KEY)
    if (!data) return []
    const favorites = safeParse(data)
    return Array.isArray(favorites) ? favorites : []
  } catch (e) {
    return []
  }
}

export function isFavorite (paperId) {
  const id = Number(paperId)
  if (isNaN(id)) return false
  const favorites = getFavorites()
  return favorites.some(item => item.id === id)
}

export function addFavorite (paperId, paperInfo = {}) {
  const id = Number(paperId)
  if (isNaN(id)) return false
  if (isFavorite(id)) return true

  const favorites = getFavorites()
  const newItem = {
    id,
    name: paperInfo.name || '',
    subjectId: paperInfo.subjectId || null,
    createdAt: Date.now()
  }

  favorites.unshift(newItem)

  if (favorites.length > FAVORITE_LIMIT) {
    favorites.splice(FAVORITE_LIMIT)
  }

  try {
    window.localStorage.setItem(FAVORITE_KEY, safeStringify(favorites))
    return true
  } catch (e) {
    return false
  }
}

export function removeFavorite (paperId) {
  const id = Number(paperId)
  if (isNaN(id)) return false

  const favorites = getFavorites()
  const newFavorites = favorites.filter(item => item.id !== id)

  try {
    window.localStorage.setItem(FAVORITE_KEY, safeStringify(newFavorites))
    return true
  } catch (e) {
    return false
  }
}

export function toggleFavorite (paperId, paperInfo = {}) {
  if (isFavorite(paperId)) {
    return removeFavorite(paperId)
  } else {
    return addFavorite(paperId, paperInfo)
  }
}

export function sortByFavorite (paperList) {
  if (!Array.isArray(paperList) || paperList.length === 0) {
    return paperList
  }

  const favoriteIds = getFavorites().map(item => item.id)
  if (favoriteIds.length === 0) {
    return paperList
  }

  const favoritePapers = []
  const otherPapers = []

  paperList.forEach(paper => {
    if (favoriteIds.includes(paper.id)) {
      favoritePapers.push(paper)
    } else {
      otherPapers.push(paper)
    }
  })

  favoritePapers.sort((a, b) => {
    const indexA = favoriteIds.indexOf(a.id)
    const indexB = favoriteIds.indexOf(b.id)
    return indexA - indexB
  })

  return [...favoritePapers, ...otherPapers]
}

export function clearFavorites () {
  try {
    window.localStorage.removeItem(FAVORITE_KEY)
    return true
  } catch (e) {
    return false
  }
}
