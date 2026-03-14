# Отчет по лаборатороной работе №1

Были реализованые следующие алгоритмы:

- [Extendible Hash Table](./app/src/main/java/com/ruskaof/algorithm/ExtendibleHashTable.java)
- [Perfect Hash](./app/src/main/java/com/ruskaof/algorithm/PerfectHash.java)
- [LSH Hash Table](./app/src/main/java/com/ruskaof/algorithm/LshHashTable.java)

Сложности алгоритмов:

## Extendible Hash Table

Вставка амортизированно работает за O(1)

![Extendible Hash Table](./graphs/hashes/extendible_hash_put_new.png)

Получение: O(1)

![Extendible Hash Table Get](./graphs/hashes/extendible_hash_get.png)

Профилирование

![Extendible Hash Table Put Prof](./graphs/hashes/extendible_hash_put_prof2.png)

Абсолютно все время тратится на IO операцию с буфером для сохранения durability записей. Стоит батчевать флашинг буфера

![Extendible Hash Table Get Prof](./graphs/hashes/extendible_hash_get_prof2.png)

Основное время тратится на использование буфера, вычисление хеша. Могут помочь оптимизации ОС или другая хеш функция


## Perfect Hash

Создание в матожидании работает за O(n)

![Perfect Hash](./graphs/hashes/perfect_hash_build.png)

Получение: O(1)

![Perfect Hash Get](./graphs/hashes/perfect_hash_get.png)

Профилирование

![Perfect Hash Build Prof](./graphs/hashes/perfect_hash_build_prof2.png)

Из нашего кода основное время тратится только на вычисление случайных чисел

![Perfect Hash Get Prof](./graphs/hashes/perfect_hash_get_prof2.png)

На графике `perfect_hash_get_prof.png` видно, что основные затраты - на хеш функцию.


## LSH Hash Table

Получение всех бакетов O(n)

![LSH Hash Table](./graphs/hashes/lsh_read.png)

Создание O(n)

![LSH Hash Table Build](./graphs/hashes/lsh_build.png)

Вставка O(1)

![LSH Hash Table Add](./graphs/hashes/lsh_put.png)

Профилирование

![LSH Hash Table Build Bench](./graphs/hashes/lsh_put_prof2.png)

Больше всего времени (из того, что можно оптимизировать) тратится на вычисление хеш функции

